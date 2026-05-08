package com.devilsvault.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRequest(
        @NotBlank @Size(max = 64) String requestType,
        @Size(max = 255) String currentValue,
        @Size(max = 255) String requestedValue,
        @Size(max = 1024) String description) { }
