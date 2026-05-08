package com.devilsvault.api.admin;

import com.devilsvault.api.audit.AuditLog;
import java.time.OffsetDateTime;

public record AuditLogDto(
        Long id,
        Long userId,
        String action,
        String details,
        String ipAddress,
        OffsetDateTime createdAt) {

    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
