package com.devilsvault.api.admin;

import com.devilsvault.api.user.User;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminUserDetailDto(
        Long id,
        String username,
        String email,
        String role,
        OffsetDateTime createdAt,
        List<AdminAccountDto> accounts) {

    public static AdminUserDetailDto from(User u, List<AdminAccountDto> accounts) {
        return new AdminUserDetailDto(u.getId(), u.getUsername(), u.getEmail(),
                u.getRole().name(), u.getCreatedAt(), accounts);
    }
}
