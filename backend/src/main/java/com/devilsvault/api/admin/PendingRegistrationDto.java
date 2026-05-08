package com.devilsvault.api.admin;

import com.devilsvault.api.user.Role;
import java.time.OffsetDateTime;

public record PendingRegistrationDto(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        PendingRegistration.Status status,
        Long requesterId,
        OffsetDateTime createdAt) {

    public static PendingRegistrationDto from(PendingRegistration p) {
        return new PendingRegistrationDto(
                p.getId(),
                p.getUsername(),
                p.getEmail(),
                p.getFullName(),
                p.getRole(),
                p.getStatus(),
                p.getRequester() == null ? null : p.getRequester().getId(),
                p.getCreatedAt());
    }
}
