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

    @Test
    void exhaustedIpBucketDoesNotConsumeVictimUserTokens() {
        // Regression for review finding 3145129717: a denied request must not
        // cost a token in both buckets. After draining the attacker IP, a
        // victim's user bucket should remain full and reachable from a
        // different IP.
        LoginRateLimiter rl = new LoginRateLimiter();
        String attackerIp = "198.51.100.1";

        // Drain the attacker IP bucket via fresh usernames.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_IP; i++) {
            assertThat(rl.tryAcquire("burner-" + i, attackerIp).allowed()).isTrue();
        }
        assertThat(rl.tryAcquire("burner-extra", attackerIp).allowed())
                .as("attacker IP is now drained")
                .isFalse();

        // Attacker now hammers a specific victim username from the drained IP.
        // Each attempt must be denied without consuming a token from the
        // victim's user bucket.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER * 2; i++) {
            assertThat(rl.tryAcquire("victim", attackerIp).allowed())
                    .as("victim attempt %d from drained IP", i)
                    .isFalse();
        }

        // The victim's user bucket must still hold the full attempt budget;
        // a different IP can authenticate the victim normally.
        for (int i = 0; i < LoginRateLimiter.MAX_ATTEMPTS_PER_USER; i++) {
            assertThat(rl.tryAcquire("victim", "203.0.113.50").allowed())
                    .as("victim from fresh IP, attempt %d", i)
                    .isTrue();
        }
    }
}
