package com.devilsvault.api.credit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreditCardDto(
        Long id,
        String maskedCardNumber,
        BigDecimal creditLimit,
        BigDecimal balance,
        BigDecimal apr,
        CreditAccount.Status status,
        OffsetDateTime createdAt) {

    public static CreditCardDto from(CreditAccount c) {
        return new CreditCardDto(
                c.getId(),
                mask(c.getCardNumber()),
                c.getCreditLimit(),
                c.getBalance(),
                c.getApr(),
                c.getStatus(),
                c.getCreatedAt());
    }

    private static String mask(String pan) {
        if (pan == null || pan.length() < 4) {
            return "****";
        }
        return "**** **** **** " + pan.substring(pan.length() - 4);
    }
}
