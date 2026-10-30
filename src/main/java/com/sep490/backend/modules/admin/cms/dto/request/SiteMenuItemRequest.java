package com.sep490.backend.modules.admin.cms.dto.request;

import com.sep490.backend.modules.admin.cms.entity.enums.SiteMenuPlacement;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SiteMenuItemRequest(
        @NotBlank(message = "Menu code is required")
        @Size(max = 100, message = "Menu code must be at most 100 characters")
        String code,

        @NotNull(message = "Menu placement is required")
        SiteMenuPlacement placement,

        @NotBlank(message = "Vietnamese menu label is required")
        @Size(max = 200, message = "Vietnamese menu label must be at most 200 characters")
        String labelVi,

        @NotBlank(message = "English menu label is required")
        @Size(max = 200, message = "English menu label must be at most 200 characters")
        String labelEn,

        @Size(max = 500, message = "Menu path must be at most 500 characters")
        String path,

        @Size(max = 100, message = "Icon key must be at most 100 characters")
        String iconKey,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be non-negative")
        Integer displayOrder,

        @NotNull(message = "Enabled is required")
        Boolean enabled,

        @NotNull(message = "Auth required is required")
        Boolean authRequired,

        @NotNull(message = "Mobile primary is required")
        Boolean mobilePrimary,

        @Size(max = 100, message = "Feature key must be at most 100 characters")
        String featureKey
) {
}
