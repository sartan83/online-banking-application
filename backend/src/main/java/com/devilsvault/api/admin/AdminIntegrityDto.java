package com.devilsvault.api.admin;

public record AdminIntegrityDto(boolean ok, Long brokenAtId, long totalEntries) {
}
