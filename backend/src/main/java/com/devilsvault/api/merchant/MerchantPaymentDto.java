package com.devilsvault.api.merchant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MerchantPaymentDto(
        Long id,
        Long authorizationId,
        Long customerAccountId,
        Long merchantAccountId,
        BigDecimal amount,
        MerchantPayment.Status status,
        String description,
        OffsetDateTime createdAt) {

    public static MerchantPaymentDto from(MerchantPayment p) {
        return new MerchantPaymentDto(
                p.getId(),
                p.getAuthorization().getId(),
                p.getCustomerAccount().getId(),
                p.getMerchantAccount().getId(),
                p.getAmount(),
                p.getStatus(),
                p.getDescription(),
                p.getCreatedAt());
    }
}
