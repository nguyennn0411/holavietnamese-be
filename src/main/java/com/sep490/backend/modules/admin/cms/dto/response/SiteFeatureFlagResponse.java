package com.sep490.backend.modules.admin.cms.dto.response;

public record SiteFeatureFlagResponse(
        String key,
        String nameVi,
        String nameEn,
        String description,
        Boolean enabled
) {
}
