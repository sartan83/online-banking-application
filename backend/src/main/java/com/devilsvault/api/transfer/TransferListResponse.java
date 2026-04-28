package com.devilsvault.api.transfer;

import java.util.List;

public record TransferListResponse(
        List<TransferItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
