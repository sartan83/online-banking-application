package com.devilsvault.api.admin;

import com.devilsvault.api.user.User;
import java.time.OffsetDateTime;

public record AdminUserDto(
        Long id,
        String username,
        String email,
        String role,
        OffsetDateTime createdAt) {

    public static AdminUserDto from(User u) {
        return new AdminUserDto(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole().name(), u.getCreatedAt());
    }
}
