package com.devilsvault.api.account;

import java.math.BigDecimal;

public record AccountDto(
        Long id,
        AccountType accountType,
        BigDecimal balance,
        String currency) {

    public static AccountDto from(Account a) {
        return new AccountDto(a.getId(), a.getAccountType(), a.getBalance(), a.getCurrency());
    }
}
