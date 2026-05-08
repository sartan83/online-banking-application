package com.devilsvault.api.admin;

import com.devilsvault.api.user.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) { }
