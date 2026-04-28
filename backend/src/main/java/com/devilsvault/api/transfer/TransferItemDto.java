package com.devilsvault.api.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransferItemDto(
        Long id,
        Long sourceAccountId,
        Long targetAccountId,
        BigDecimal amount,
        String currency,
        String memo,
        OffsetDateTime occurredAt,
        TransferDirection direction) {

    public static TransferItemDto from(Transfer t, TransferDirection direction) {
        return new TransferItemDto(
                t.getId(),
                t.getSource().getId(),
                t.getTarget().getId(),
                t.getAmount(),
                t.getCurrency(),
                t.getDescription(),
                t.getCreatedAt(),
                direction);
    }
}
