package com.devilsvault.api.admin;

import com.devilsvault.api.audit.AuditEvent;
import java.time.OffsetDateTime;

public record AdminAuditDto(
        Long id,
        OffsetDateTime occurredAt,
        String eventType,
        String outcome,
        String actorUsername,
        String resourceType,
        String resourceId,
        String payload,
        String entryHash,
        String prevHash) {

    public static AdminAuditDto from(AuditEvent e) {
        return new AdminAuditDto(e.getId(), e.getOccurredAt(),
                e.getEventType().name(), e.getOutcome().name(),
                e.getActorUsername(), e.getResourceType(), e.getResourceId(),
                e.getPayload(), e.getEntryHash(), e.getPrevHash());
    }
}
