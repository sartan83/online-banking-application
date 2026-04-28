package com.devilsvault.api.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stateless AES-256-GCM encryption utilities shared by Flyway migrations
 * and the JPA AttributeConverter. No Spring dependency so that Flyway
 * Java-based migrations (which run before the application context) can
 * use them.
 */
public final class EncryptionUtil {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int AES_256_KEY_BYTES = 32;

    private EncryptionUtil() { }

    public static byte[] encrypt(String plaintext, byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-256-GCM encryption failed", e);
        }
    }

    public static String decrypt(byte[] data, byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-256-GCM decryption failed", e);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 hashing failed", e);
        }
    }

    public static byte[] decodeKey(String base64Key) {
        byte[] key = Base64.getDecoder().decode(base64Key);
        if (key.length != AES_256_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "DB_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256); got " + key.length);
        }
        return key;
    }

    public static String resolveKey() {
        String key = System.getenv("DB_ENCRYPTION_KEY");
        if (key == null || key.isEmpty()) {
            key = System.getProperty("DB_ENCRYPTION_KEY", "");
        }
        return key;
    }
}
