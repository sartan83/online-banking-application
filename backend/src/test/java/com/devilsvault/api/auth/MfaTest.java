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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.beans.factory.annotation.Qualifier;

@SpringBootTest
@ActiveProfiles("test")
class MfaTest {

    @Autowired WebApplicationContext wac;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy springSecurityFilterChain;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired MfaService mfaService;
    @Autowired JwtService jwtService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    private User createAdminUser(String username, String email, String password) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName("Admin " + username);
        u.setRole(Role.ADMIN);
        return users.save(u);
    }

    private User createCustomerUser(String username, String email, String password) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setFullName("Customer " + username);
        u.setRole(Role.CUSTOMER);
        return users.save(u);
    }

    // 1. Enroll → verify → mfaEnabled flips
    @Test
    void enrollAndVerify_flipsMfaEnabled() throws Exception {
        createAdminUser("mfa_admin1", "mfa_admin1@test.com", "pass1234");
        String token = jwtService.issue("mfa_admin1", "ADMIN");

        // Enroll
        MvcResult enrollResult = mvc().perform(post("/api/auth/mfa/enroll")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUrl").isNotEmpty())
                .andExpect(jsonPath("$.recoveryCodes").isArray())
                .andReturn();

        // Get the user's secret and generate a valid code
        User user = users.findByUsername("mfa_admin1").orElseThrow();
        String secret = user.getMfaSecret();
        String validCode = generateValidTotpCode(secret);

        // Verify with valid code
        String verifyBody = mapper.writeValueAsString(java.util.Map.of("code", validCode));
        mvc().perform(post("/api/auth/mfa/verify")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(true));

        User updated = users.findByUsername("mfa_admin1").orElseThrow();
        assertThat(updated.isMfaEnabled()).isTrue();
    }

    // 2. Login with MFA returns partialToken
    @Test
    void loginWithMfa_returnsPartialToken() throws Exception {
        User u = createAdminUser("mfa_admin2", "mfa_admin2@test.com", "pass1234");
        u.setMfaSecret(mfaService.generateSecret());
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        users.save(u);

        String loginBody = mapper.writeValueAsString(java.util.Map.of(
                "username", "mfa_admin2", "password", "pass1234"));
        mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.partialToken").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    // 3. login/mfa with wrong code returns 401
    @Test
    void loginMfa_wrongCode_returns401() throws Exception {
        User u = createAdminUser("mfa_admin3", "mfa_admin3@test.com", "pass1234");
        u.setMfaSecret(mfaService.generateSecret());
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        u.setMfaRecoveryCodesHash(mfaService.hashRecoveryCodes(
                java.util.List.of("1111111111", "2222222222")));
        users.save(u);

        String partialToken = jwtService.issuePartialMfaToken("mfa_admin3", "ADMIN");
        String body = mapper.writeValueAsString(java.util.Map.of(
                "partialToken", partialToken, "code", "000000"));
        mvc().perform(post("/api/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // 4. login/mfa with correct TOTP code returns access+refresh
    @Test
    void loginMfa_correctCode_returnsTokens() throws Exception {
        User u = createAdminUser("mfa_admin4", "mfa_admin4@test.com", "pass1234");
        String secret = mfaService.generateSecret();
        u.setMfaSecret(secret);
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        users.save(u);

        String partialToken = jwtService.issuePartialMfaToken("mfa_admin4", "ADMIN");
        String validCode = generateValidTotpCode(secret);
        String body = mapper.writeValueAsString(java.util.Map.of(
                "partialToken", partialToken, "code", validCode));
        mvc().perform(post("/api/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    // 5. Recovery code is single-use
    @Test
    void recoveryCode_singleUse() throws Exception {
        User u = createAdminUser("mfa_admin5", "mfa_admin5@test.com", "pass1234");
        String secret = mfaService.generateSecret();
        java.util.List<String> codes = java.util.List.of("9999999999", "8888888888");
        u.setMfaSecret(secret);
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        u.setMfaRecoveryCodesHash(mfaService.hashRecoveryCodes(codes));
        users.save(u);

        // First use - should work
        String partialToken = jwtService.issuePartialMfaToken("mfa_admin5", "ADMIN");
        String body = mapper.writeValueAsString(java.util.Map.of(
                "partialToken", partialToken, "code", "9999999999"));
        mvc().perform(post("/api/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Second use - should fail
        String partialToken2 = jwtService.issuePartialMfaToken("mfa_admin5", "ADMIN");
        String body2 = mapper.writeValueAsString(java.util.Map.of(
                "partialToken", partialToken2, "code", "9999999999"));
        mvc().perform(post("/api/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isUnauthorized());
    }

    // 6. Non-admin can use MFA optionally
    @Test
    void nonAdmin_canUseMfaOptionally() throws Exception {
        createCustomerUser("mfa_cust1", "mfa_cust1@test.com", "pass1234");
        String token = jwtService.issue("mfa_cust1", "CUSTOMER");

        // Enroll
        mvc().perform(post("/api/auth/mfa/enroll")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty());

        // Verify with valid code
        User user = users.findByUsername("mfa_cust1").orElseThrow();
        String validCode = generateValidTotpCode(user.getMfaSecret());
        String verifyBody = mapper.writeValueAsString(java.util.Map.of("code", validCode));
        mvc().perform(post("/api/auth/mfa/verify")
                        .with(csrf())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(true));
    }

    // 7. Admin without MFA is blocked from /api/admin/**
    @Test
    void adminWithoutMfa_blockedFromAdminEndpoints() throws Exception {
        createAdminUser("mfa_admin7", "mfa_admin7@test.com", "pass1234");
        String token = jwtService.issue("mfa_admin7", "ADMIN");

        MvcResult result = mvc().perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("mfa_required");
    }

    // 8. Partial token expiry (test with expired token)
    @Test
    void partialToken_expired_returns401() throws Exception {
        User u = createAdminUser("mfa_admin8", "mfa_admin8@test.com", "pass1234");
        String secret = mfaService.generateSecret();
        u.setMfaSecret(secret);
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        users.save(u);

        // Create an expired partial token by manually building it
        String expiredToken = createExpiredPartialToken("mfa_admin8", "ADMIN");
        String validCode = generateValidTotpCode(secret);
        String body = mapper.writeValueAsString(java.util.Map.of(
                "partialToken", expiredToken, "code", validCode));
        mvc().perform(post("/api/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // 9. Partial MFA token cannot be used as access token
    @Test
    void partialMfaToken_cannotAccessProtectedEndpoints() throws Exception {
        User u = createAdminUser("mfa_admin9", "mfa_admin9@test.com", "pass1234");
        String secret = mfaService.generateSecret();
        u.setMfaSecret(secret);
        u.setMfaEnabled(true);
        u.setMfaEnrolledAt(java.time.OffsetDateTime.now());
        users.save(u);

        String partialToken = jwtService.issuePartialMfaToken("mfa_admin9", "ADMIN");
        mvc().perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + partialToken))
                .andExpect(status().isForbidden());
    }

    private String generateValidTotpCode(String secret) {
        try {
            com.eatthepath.otp.TimeBasedOneTimePasswordGenerator totp =
                    new com.eatthepath.otp.TimeBasedOneTimePasswordGenerator(
                            java.time.Duration.ofSeconds(30), 6, "HmacSHA1");
            byte[] keyBytes = java.util.Base64.getUrlDecoder().decode(secret);
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA1");
            int code = totp.generateOneTimePassword(key, java.time.Instant.now());
            return String.format("%06d", code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }

    private String createExpiredPartialToken(String username, String role) {
        // Build a JWT that's already expired
        java.time.Instant past = java.time.Instant.now().minus(java.time.Duration.ofMinutes(10));
        return io.jsonwebtoken.Jwts.builder()
                .subject(username)
                .issuer("devilsvault")
                .audience().add("devilsvault-api").and()
                .issuedAt(java.util.Date.from(past.minus(java.time.Duration.ofMinutes(5))))
                .expiration(java.util.Date.from(past))
                .id(java.util.UUID.randomUUID().toString())
                .claim("scope", role)
                .claim("mfa_pending", true)
                .signWith(jwtService.getPublicKey() instanceof java.security.interfaces.RSAPublicKey
                        ? getPrivateKeyReflection() : getPrivateKeyReflection(),
                        io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    private java.security.PrivateKey getPrivateKeyReflection() {
        try {
            java.lang.reflect.Field f = JwtService.class.getDeclaredField("privateKey");
            f.setAccessible(true);
            return (java.security.PrivateKey) f.get(jwtService);
        } catch (Exception e) {
            throw new RuntimeException("Cannot access privateKey", e);
        }
    }

}
