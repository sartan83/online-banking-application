package com.devilsvault.api.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class MfaService {

    private static final int SECRET_BYTES = 20;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 10;
    private static final String TOTP_ALGORITHM = "HmacSHA1";
    private static final Duration TOTP_STEP = Duration.ofSeconds(30);
    private static final int TOTP_DIGITS = 6;
    private static final String TOTP_ISSUER = "DevilsVault";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String toBase32(String rawSecret) {
        byte[] bytes = decodeSecret(rawSecret);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUrl(String username, String base32Secret) {
        String encodedUsername = java.net.URLEncoder.encode(username, StandardCharsets.UTF_8);
        String encodedIssuer = java.net.URLEncoder.encode(TOTP_ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + encodedIssuer + ":" + encodedUsername
                + "?secret=" + base32Secret
                + "&issuer=" + encodedIssuer
                + "&algorithm=SHA1&digits=" + TOTP_DIGITS
                + "&period=" + TOTP_STEP.getSeconds();
    }

    public boolean verifyCode(String secret, String code) {
        try {
            TimeBasedOneTimePasswordGenerator totp =
                    new TimeBasedOneTimePasswordGenerator(TOTP_STEP, TOTP_DIGITS, TOTP_ALGORITHM);
            byte[] keyBytes = decodeSecret(secret);
            SecretKeySpec key = new SecretKeySpec(keyBytes, TOTP_ALGORITHM);
            Instant now = Instant.now();
            for (int window = -1; window <= 1; window++) {
                Instant t = now.plus(TOTP_STEP.multipliedBy(window));
                int generated = totp.generateOneTimePassword(key, t);
                String formatted = String.format("%0" + TOTP_DIGITS + "d", generated);
                if (formatted.equals(code)) {
                    return true;
                }
            }
        } catch (InvalidKeyException ex) {
            return false;
        }
        return false;
    }

    public List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                sb.append(secureRandom.nextInt(10));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    public String hashRecoveryCodes(List<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(sha256Hex(codes.get(i)));
        }
        return sb.toString();
    }

    public boolean verifyRecoveryCode(String storedHashes, String code) {
        if (storedHashes == null || storedHashes.isBlank()) {
            return false;
        }
        byte[] codeHash = sha256Hex(code).getBytes(StandardCharsets.UTF_8);
        String[] hashes = storedHashes.split("\n");
        for (String hash : hashes) {
            if (MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), codeHash)) {
                return true;
            }
        }
        return false;
    }

    public String consumeRecoveryCode(String storedHashes, String code) {
        if (storedHashes == null || storedHashes.isBlank()) {
            return storedHashes;
        }
        byte[] codeHash = sha256Hex(code).getBytes(StandardCharsets.UTF_8);
        String[] hashes = storedHashes.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String hash : hashes) {
            if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8), codeHash)) {
                if (!first) {
                    sb.append('\n');
                }
                sb.append(hash);
                first = false;
            }
        }
        return sb.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static byte[] decodeSecret(String secret) {
        return Base64.getUrlDecoder().decode(secret);
    }

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                sb.append(BASE32_CHARS.charAt(index));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_CHARS.charAt(index));
        }
        return sb.toString();
    }
}
