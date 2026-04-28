package com.devilsvault.api.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JtiRevocationCache {

    private static final long MAX_ENTRIES = 10_000L;

    private final Cache<String, Boolean> revokedJtis;

    public JtiRevocationCache(
            @Value("${app.security.jwt.expiration-minutes}") long expirationMinutes) {
        this.revokedJtis = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(Duration.ofMinutes(expirationMinutes))
                .build();
    }

    public void revoke(String jti) {
        revokedJtis.put(jti, Boolean.TRUE);
    }

    public boolean isRevoked(String jti) {
        return revokedJtis.getIfPresent(jti) != null;
    }
}
