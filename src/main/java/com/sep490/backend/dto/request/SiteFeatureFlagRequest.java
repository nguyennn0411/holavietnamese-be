package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SiteFeatureFlagRequest(
        @NotBlank(message = "Feature key is required")
        @Size(max = 100, message = "Feature key must be at most 100 characters")
        String key,

        @NotBlank(message = "Vietnamese feature name is required")
        @Size(max = 200) String nameVi,

        @NotBlank(message = "English feature name is required")
        @Size(max = 200) String nameEn,

        @Size(max = 1000) String description,

        @NotNull(message = "Enabled is required")
        Boolean enabled
) {
}
