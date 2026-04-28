package com.devilsvault.api.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountDetailDto(
        Long id,
        AccountType accountType,
        BigDecimal balance,
        String currency,
        OffsetDateTime openedAt) {

    public static AccountDetailDto from(Account a) {
        return new AccountDetailDto(
                a.getId(), a.getAccountType(), a.getBalance(), a.getCurrency(), a.getCreatedAt());
    }
}
