package com.devilsvault.api.admin;

import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import java.time.OffsetDateTime;

public record UserSummary(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        String category,
        boolean enabled,
        OffsetDateTime createdAt) {

    public static UserSummary from(User u) {
        return new UserSummary(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getFullName(),
                u.getRole(),
                u.getRole().category().label(),
                u.isEnabled(),
                u.getCreatedAt());
    }
}
