package com.devilsvault.api.request;

import java.time.OffsetDateTime;

public record RequestDto(
        Long id,
        Long requesterId,
        String requesterUsername,
        String requestType,
        String currentValue,
        String requestedValue,
        Request.Status status,
        Request.Scope scope,
        Long approverId,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static RequestDto from(Request r) {
        return new RequestDto(
                r.getId(),
                r.getRequester().getId(),
                r.getRequester().getUsername(),
                r.getRequestType(),
                r.getCurrentValue(),
                r.getRequestedValue(),
                r.getStatus(),
                r.getScope(),
                r.getApprover() == null ? null : r.getApprover().getId(),
                r.getDescription(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
