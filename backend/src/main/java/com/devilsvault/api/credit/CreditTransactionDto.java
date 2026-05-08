package com.devilsvault.api.credit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreditTransactionDto(
        Long id,
        CreditTransaction.Kind kind,
        BigDecimal amount,
        Long sourceAccountId,
        String description,
        OffsetDateTime createdAt) {

    public static CreditTransactionDto from(CreditTransaction t) {
        return new CreditTransactionDto(
                t.getId(),
                t.getKind(),
                t.getAmount(),
                t.getSourceAccount() == null ? null : t.getSourceAccount().getId(),
                t.getDescription(),
                t.getCreatedAt());
    }
}
