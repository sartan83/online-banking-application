package com.devilsvault.api.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferResponse(
        Long id,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String currency,
        String description,
        Transfer.Status status,
        OffsetDateTime createdAt) {

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getSource().getId(),
                t.getTarget().getId(),
                t.getAmount(),
                t.getCurrency(),
                t.getDescription(),
                t.getStatus(),
                t.getCreatedAt());
    }
}
