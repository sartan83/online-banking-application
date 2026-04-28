package com.devilsvault.api.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StatementEntry(
        Long id,
        OffsetDateTime occurredAt,
        String direction,
        BigDecimal amount,
        String currency,
        Long counterpartAccountId,
        String memo,
        BigDecimal runningBalance) {
}
