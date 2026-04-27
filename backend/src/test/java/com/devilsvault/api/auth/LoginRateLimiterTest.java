package com.devilsvault.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    @Test
    void allowsUpToFiveAttemptsPerUserThenDenies() {
        LoginRateLimiter rl = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            LoginRateLimiter.Decision d = rl.tryAcquire("alice", "10.0.0." + i);
            assertThat(d.allowed()).as("attempt %d", i).isTrue();
        }

        LoginRateLimiter.Decision denied = rl.tryAcquire("alice", "10.0.0.99");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isPositive();
    }

    @Test
    void successfulLoginResetsUserBucket() {
        LoginRateLimiter rl = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            assertThat(rl.tryAcquire("bob", "10.0.0.1").allowed()).isTrue();
        }
        assertThat(rl.tryAcquire("bob", "10.0.0.1").allowed()).isFalse();

        rl.onSuccessfulLogin("bob");

        assertThat(rl.tryAcquire("bob", "10.0.0.1").allowed())
                .as("after onSuccessfulLogin the user bucket is refilled")
                .isTrue();
    }

    @Test
    void ipBucketIsConsumedAcrossUsernames() {
        LoginRateLimiter rl = new LoginRateLimiter();

        // Twenty attempts from one IP across many usernames consume the IP bucket
        // (the per-user limit is never hit since each username is fresh).
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_IP; i++) {
            assertThat(rl.tryAcquire("user-" + i, "192.0.2.1").allowed()).isTrue();
        }

        LoginRateLimiter.Decision denied = rl.tryAcquire("user-fresh", "192.0.2.1");
        assertThat(denied.allowed())
                .as("IP bucket should be exhausted independent of username")
                .isFalse();
        assertThat(denied.retryAfter()).isPositive();
    }

    @Test
    void differentIpsDoNotShareBuckets() {
        LoginRateLimiter rl = new LoginRateLimiter();

        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_IP; i++) {
            assertThat(rl.tryAcquire("u" + i, "203.0.113.1").allowed()).isTrue();
        }
        assertThat(rl.tryAcquire("u-extra", "203.0.113.1").allowed()).isFalse();

        // A fresh IP can still authenticate.
        assertThat(rl.tryAcquire("u-extra", "203.0.113.2").allowed()).isTrue();
    }
}
