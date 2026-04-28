package com.devilsvault.api.account;

import java.util.List;

public record StatementPage(
        List<StatementEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
