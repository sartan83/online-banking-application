package com.devilsvault.api.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring-managed encryption service that reads the key from the
 * application environment and delegates to {@link EncryptionUtil}.
 */
@Component
public final class EncryptionService {

    private final byte[] key;

    public EncryptionService(
            @Value("${DB_ENCRYPTION_KEY:#{null}}") String envKey,
            @Value("${app.encryption.key:#{null}}") String propKey) {
        String raw = envKey != null && !envKey.isEmpty() ? envKey : propKey;
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException(
                    "DB_ENCRYPTION_KEY env var or app.encryption.key property must be set");
        }
        this.key = EncryptionUtil.decodeKey(raw);
    }

    public byte[] encrypt(String plaintext) {
        return EncryptionUtil.encrypt(plaintext, key);
    }

    public String decrypt(byte[] ciphertext) {
        return EncryptionUtil.decrypt(ciphertext, key);
    }

    public String sha256Hex(String input) {
        return EncryptionUtil.sha256Hex(input);
    }
}
