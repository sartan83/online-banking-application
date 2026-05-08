package com.devilsvault.api.merchant;

import java.time.OffsetDateTime;

public record MerchantAuthorizationDto(
        Long id,
        Long customerId,
        String customerUsername,
        Long merchantId,
        String merchantUsername,
        MerchantAuthorization.Status status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static MerchantAuthorizationDto from(MerchantAuthorization a) {
        return new MerchantAuthorizationDto(
                a.getId(),
                a.getCustomer().getId(),
                a.getCustomer().getUsername(),
                a.getMerchant().getId(),
                a.getMerchant().getUsername(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
