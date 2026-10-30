package com.sep490.backend.modules.admin.cms.dto.request;

import com.sep490.backend.modules.admin.cms.entity.enums.SiteBannerActionType;
import com.sep490.backend.modules.admin.cms.entity.enums.SiteBannerPlacement;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record SiteBannerRequest(
        @NotBlank(message = "Banner code is required")
        @Size(max = 100, message = "Banner code must be at most 100 characters")
        String code,

        @NotNull(message = "Banner placement is required")
        SiteBannerPlacement placement,

        @Size(max = 255) String eyebrowVi,
        @Size(max = 255) String eyebrowEn,

        @NotBlank(message = "Vietnamese banner title is required")
        @Size(max = 255) String titleVi,

        @NotBlank(message = "English banner title is required")
        @Size(max = 255) String titleEn,

        @Size(max = 2000) String descriptionVi,
        @Size(max = 2000) String descriptionEn,

        @NotBlank(message = "Banner image URL is required")
        @Size(max = 2048) String imageUrl,

        @Size(max = 500) String imageAltVi,
        @Size(max = 500) String imageAltEn,
        @Size(max = 255) String photoLabelVi,
        @Size(max = 255) String photoLabelEn,

        @NotNull(message = "Banner action type is required")
        SiteBannerActionType actionType,

        @Size(max = 255) String actionLabelVi,
        @Size(max = 255) String actionLabelEn,
        @Size(max = 2048) String actionTarget,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order must be non-negative")
        Integer displayOrder,

        @NotNull(message = "Enabled is required")
        Boolean enabled,

        Instant startsAt,
        Instant endsAt,

        @Size(max = 100, message = "Feature key must be at most 100 characters")
        String featureKey
) {
}
