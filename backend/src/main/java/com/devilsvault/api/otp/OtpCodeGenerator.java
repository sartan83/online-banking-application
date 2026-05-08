package com.devilsvault.api.otp;

import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HOTP-style code generator ported from {@code dao/userauthentication/OneTimePassword}. Replaces
 * the legacy reflection-based truncation with the standard RFC 4226 dynamic truncation, but keeps
 * the same overall shape: HMAC-SHA-256 of an 8-byte moving factor seeded with cryptographically
 * random bytes, mapped down to a fixed-width decimal code.
 */
@Component
public class OtpCodeGenerator {

    private static final int CODE_DIGITS = 6;
    private static final int[] DIGITS_POWER = {
            1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000
    };

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        long movingFactor = random.nextLong() & Long.MAX_VALUE;
        try {
            return computeHotp(secret, movingFactor);
        } catch (GeneralSecurityFailure ex) {
            throw new IllegalStateException("Unable to generate OTP code", ex);
        }
    }

    private static String computeHotp(byte[] secret, long movingFactor) throws GeneralSecurityFailure {
        byte[] text = new byte[8];
        long factor = movingFactor;
        for (int i = text.length - 1; i >= 0; i--) {
            text[i] = (byte) (factor & 0xff);
            factor >>= 8;
        }
        byte[] hash = hmacSha256(secret, text);
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % DIGITS_POWER[CODE_DIGITS];
        StringBuilder sb = new StringBuilder(Integer.toString(otp));
        while (sb.length() < CODE_DIGITS) {
            sb.insert(0, '0');
        }
        return sb.toString();
    }

    private static byte[] hmacSha256(byte[] keyBytes, byte[] text) throws GeneralSecurityFailure {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "RAW"));
            return mac.doFinal(text);
        } catch (java.security.GeneralSecurityException ex) {
            throw new GeneralSecurityFailure(ex);
        }
    }

    private static final class GeneralSecurityFailure extends Exception {
        GeneralSecurityFailure(Throwable cause) {
            super(cause);
        }
    }
}
