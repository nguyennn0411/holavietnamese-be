package com.sep490.backend.dto.response;

public record SiteFeatureFlagResponse(
        String key,
        String nameVi,
        String nameEn,
        String description,
        Boolean enabled
) {
}
