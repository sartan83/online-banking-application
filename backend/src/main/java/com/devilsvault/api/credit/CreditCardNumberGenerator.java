package com.devilsvault.api.credit;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates a 16-digit Visa-test PAN (BIN {@code 4111}) terminated with a Luhn check digit.
 *
 * <p>Replaces the legacy {@code dao/bankaccount/CreditCardGenerator} which produced syntactically
 * valid Mastercard test numbers; the BIN choice is arbitrary but kept deterministic to mirror the
 * legacy semantics of "every account gets a card-number-shaped string".
 */
@Component
public class CreditCardNumberGenerator {

    private static final String BIN = "4111";
    private static final int LENGTH = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder partial = new StringBuilder(BIN);
        while (partial.length() < LENGTH - 1) {
            partial.append(random.nextInt(10));
        }
        partial.append(luhnCheckDigit(partial.toString()));
        return partial.toString();
    }

    private static final int LUHN_BASE = 10;

    private static int luhnCheckDigit(String number) {
        int sum = 0;
        boolean alt = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n >= LUHN_BASE) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return (LUHN_BASE - (sum % LUHN_BASE)) % LUHN_BASE;
    }
}
