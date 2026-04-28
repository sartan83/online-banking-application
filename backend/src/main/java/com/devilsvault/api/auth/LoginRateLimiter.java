package com.devilsvault.api.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Rate limiter for the login endpoint. Backs DORA-2.1 (Art. 9 / Annex II
 * strong-authentication and protection-against-brute-force requirements).
 *
 * <p>Two independent buckets are consumed on every attempt: one keyed by
 * username (defends a single account against credential stuffing) and one
 * keyed by client IP (defends against username enumeration / spraying).
 * If either bucket is depleted, the request is refused with the longer of
 * the two refill durations.
 *
 * <p>Successful authentications reset the username bucket so a legitimate
 * user is not locked out after a few mistakes followed by a correct
 * password. The IP bucket is intentionally not reset on success — a single
 * compromised host should not be allowed to fan out across many usernames.
 *
 * <p>Buckets are held in Caffeine caches with a TTL slightly longer than
 * the rate-limit window to avoid unbounded memory growth. The interface is
 * a Spring component so it can later be swapped for a Redis-backed
 * implementation when we deploy multiple backend instances.
 */
@Component
public class LoginRateLimiter {

    static final int MAX_ATTEMPTS_PER_USER = 5;
    static final int MAX_ATTEMPTS_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final Duration CACHE_TTL = WINDOW.plus(Duration.ofMinutes(5));
    private static final long CACHE_MAX_SIZE = 100_000L;

    private final Cache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(CACHE_TTL)
            .maximumSize(CACHE_MAX_SIZE)
            .build();

    private final Cache<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .expireAfterAccess(CACHE_TTL)
            .maximumSize(CACHE_MAX_SIZE)
            .build();

    /**
     * Try to consume one attempt against both the username and IP buckets.
     * If either is exhausted, the result is {@link Decision#denied(Duration)}
     * with a {@code retryAfter} duration suitable for the {@code Retry-After}
     * HTTP header.
     */
    public Decision tryAcquire(String username, String ip) {
        Bucket userBucket = userBuckets.get(username, k -> newBucket(MAX_ATTEMPTS_PER_USER));
        Bucket ipBucket = ipBuckets.get(ip, k -> newBucket(MAX_ATTEMPTS_PER_IP));

        // Consume sequentially with rollback so a denied request never costs a
        // token in *both* buckets. If either bucket is empty, no other bucket is
        // touched. This prevents an attacker who has already drained their own
        // IP bucket from continuing to drain a victim's user bucket: once the
        // IP token-consume here fails, the user token we just spent is restored.
        ConsumptionProbe userProbe = userBucket.tryConsumeAndReturnRemaining(1);
        if (!userProbe.isConsumed()) {
            return Decision.deny(Duration.ofNanos(userProbe.getNanosToWaitForRefill()));
        }

        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            userBucket.addTokens(1);
            return Decision.deny(Duration.ofNanos(ipProbe.getNanosToWaitForRefill()));
        }

        return Decision.permit();
    }

    /**
     * Reset the username bucket so a successful login restores the full
     * attempt budget. Does not affect the IP bucket.
     */
    public void onSuccessfulLogin(String username) {
        userBuckets.invalidate(username);
    }

    /**
     * Clear every per-user and per-IP bucket. Intended for two operational
     * use cases: incident response (an admin needs to lift a global lockout
     * caused by a misconfigured upstream proxy) and integration tests that
     * share a Spring application context across @SpringBootTest classes —
     * in tests the singleton caches would otherwise leak attempts between
     * unrelated test classes.
     */
    public void resetAll() {
        userBuckets.invalidateAll();
        ipBuckets.invalidateAll();
    }

    private static Bucket newBucket(int capacity) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, WINDOW)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public record Decision(boolean allowed, Duration retryAfter) {

        public static Decision permit() {
            return new Decision(true, Duration.ZERO);
        }

        public static Decision deny(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
