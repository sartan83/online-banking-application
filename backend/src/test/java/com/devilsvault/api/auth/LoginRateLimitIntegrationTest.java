package com.devilsvault.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class LoginRateLimitIntegrationTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    LoginRateLimiter rateLimiter;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(wac).addFilter(springSecurityFilterChain).build();
    }

    private void seedUser(String username) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPasswordHash(encoder.encode("correct-horse"));
        u.setFullName(username);
        u.setRole(Role.CUSTOMER);
        users.save(u);
    }

    @Test
    void sixFailedLoginsForSameUserReturns429WithRetryAfter() throws Exception {
        seedUser("rl-user-1");
        // Reset in case other tests in this class share state via the same bean.
        rateLimiter.onSuccessfulLogin("rl-user-1");

        MockMvc mvc = mvc();
        String body = "{\"username\":\"rl-user-1\",\"password\":\"wrong-pw\"}";

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String retryAfter = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).isNotNull();
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    void successfulLoginResetsBucketSoLegitimateUserIsNotLockedOut() throws Exception {
        seedUser("rl-user-2");
        rateLimiter.onSuccessfulLogin("rl-user-2");

        MockMvc mvc = mvc();
        String wrong = "{\"username\":\"rl-user-2\",\"password\":\"wrong-pw\"}";
        String right = "{\"username\":\"rl-user-2\",\"password\":\"correct-horse\"}";

        // Four wrong attempts (one short of the limit), then a correct password.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER - 1; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(right))
                .andExpect(status().isOk());

        // Bucket must be refilled by the successful login: another full set of
        // wrong attempts should still be allowed before lockout.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
    }
}
