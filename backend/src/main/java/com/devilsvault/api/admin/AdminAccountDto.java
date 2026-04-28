package com.devilsvault.api.admin;

import com.devilsvault.api.account.Account;
import java.math.BigDecimal;

public record AdminAccountDto(
        Long id,
        String type,
        BigDecimal balance,
        String status) {

    public static AdminAccountDto from(Account a) {
        return new AdminAccountDto(a.getId(), a.getAccountType().name(),
                a.getBalance(), a.getStatus().name());
    }
}
