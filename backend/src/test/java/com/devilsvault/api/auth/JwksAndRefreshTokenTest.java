package com.devilsvault.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JwksAndRefreshTokenTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwtService;
    @Autowired LoginRateLimiter rateLimiter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
        // The rate-limiter is a singleton across @SpringBootTest classes, so prior
        // test classes that exercised /api/auth/login may have drained the
        // per-IP bucket for 127.0.0.1. Reset before each test so legitimate
        // logins inside this class never bounce off a stale 429.
        rateLimiter.resetAll();
    }

    private JsonNode loginUser(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private User seedUser(String username) {
        if (users.findByUsername(username).isPresent()) {
            return users.findByUsername(username).get();
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(encoder.encode("password123"));
        u.setFullName(username.substring(0, 1).toUpperCase() + username.substring(1));
        u.setRole(Role.CUSTOMER);
        return users.save(u);
    }

    private User seedAdmin(String username) {
        if (users.findByUsername(username).isPresent()) {
            return users.findByUsername(username).get();
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPasswordHash(encoder.encode("password123"));
        u.setFullName("Admin " + username);
        u.setRole(Role.ADMIN);
        u.setMfaEnabled(true);
        return users.save(u);
    }

    @Test
    void jwksEndpointReturnsValidJson() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists());
    }

    @Test
    void accessTokensAreRs256() throws Exception {
        seedUser("rs256test");
        JsonNode loginResp = loginUser("rs256test", "password123");
        String accessToken = loginResp.get("accessToken").asText();

        // Decode the JWT header to verify RS256
        String[] parts = accessToken.split("\\.");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode header = mapper.readTree(headerJson);
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.has("kid")).isTrue();

        // Verify the token can be parsed with the public key
        Claims claims = jwtService.parse(accessToken);
        assertThat(claims.getSubject()).isEqualTo("rs256test");
        assertThat(claims.getIssuer()).isEqualTo("devilsvault");
        assertThat(claims.getAudience()).contains("devilsvault-api");
        assertThat(claims.getId()).isNotNull();
        assertThat(claims.get("scope", String.class)).isEqualTo("CUSTOMER");
    }

    @Test
    void loginReturnsAccessAndRefreshToken() throws Exception {
        seedUser("logintest");
        JsonNode resp = loginUser("logintest", "password123");
        assertThat(resp.has("accessToken")).isTrue();
        assertThat(resp.has("refreshToken")).isTrue();
        assertThat(resp.has("expiresIn")).isTrue();
        assertThat(resp.get("accessToken").asText()).isNotBlank();
        assertThat(resp.get("refreshToken").asText()).isNotBlank();
        assertThat(resp.get("expiresIn").asLong()).isGreaterThan(0);
    }

    @Test
    void refreshRotatesCorrectly() throws Exception {
        seedUser("refreshtest");
        JsonNode loginResp = loginUser("refreshtest", "password123");
        String refreshToken = loginResp.get("refreshToken").asText();

        String body = mapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
        MvcResult result = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode refreshResp = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(refreshResp.get("accessToken").asText()).isNotBlank();
        assertThat(refreshResp.get("refreshToken").asText()).isNotBlank();
        // New refresh token should be different from the old one
        assertThat(refreshResp.get("refreshToken").asText()).isNotEqualTo(refreshToken);
    }

    @Test
    void refreshReuseDetectionRevokesAllTokens() throws Exception {
        seedUser("reusetest");
        JsonNode loginResp = loginUser("reusetest", "password123");
        String firstRefreshToken = loginResp.get("refreshToken").asText();

        // Use the refresh token once (valid rotation)
        String body = mapper.writeValueAsString(java.util.Map.of("refreshToken", firstRefreshToken));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Try to reuse the same (now-revoked) refresh token — should fail
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        seedUser("logouttest");
        JsonNode loginResp = loginUser("logouttest", "password123");
        String accessToken = loginResp.get("accessToken").asText();
        String refreshToken = loginResp.get("refreshToken").asText();

        String body = mapper.writeValueAsString(java.util.Map.of("refreshToken", refreshToken));
        mvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        // Refresh with the revoked token should fail
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRevokeBlocksAccessToken() throws Exception {
        User target = seedUser("adminrevoketarget");
        User admin = seedAdmin("adminrevokecaller");

        JsonNode targetLogin = loginUser("adminrevoketarget", "password123");
        String targetAccessToken = targetLogin.get("accessToken").asText();

        // Verify target can access their accounts
        mvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + targetAccessToken))
                .andExpect(status().isOk());

        // Admin revokes target's sessions (use direct JWT since MFA-enabled admin can't log in via API)
        String adminAccessToken = jwtService.issue(admin.getUsername(), admin.getRole().name());

        mvc.perform(post("/api/admin/sessions/" + target.getId() + "/revoke")
                        .with(csrf())
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutAllRevokesAllRefreshTokens() throws Exception {
        seedUser("logoutalltest");
        JsonNode login1 = loginUser("logoutalltest", "password123");
        String access1 = login1.get("accessToken").asText();
        String refresh1 = login1.get("refreshToken").asText();

        JsonNode login2 = loginUser("logoutalltest", "password123");
        String refresh2 = login2.get("refreshToken").asText();

        // logout-all
        mvc.perform(post("/api/auth/logout-all")
                        .with(csrf())
                        .header("Authorization", "Bearer " + access1))
                .andExpect(status().isNoContent());

        // Both refresh tokens should now be invalid
        String body1 = mapper.writeValueAsString(java.util.Map.of("refreshToken", refresh1));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isUnauthorized());

        String body2 = mapper.writeValueAsString(java.util.Map.of("refreshToken", refresh2));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshEndpointWithInvalidTokenReturns401() throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("refreshToken", "invalid-token-value"));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
