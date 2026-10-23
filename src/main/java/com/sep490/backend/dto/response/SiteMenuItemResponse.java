package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.SiteMenuPlacement;

public record SiteMenuItemResponse(
        Long id,
        String code,
        SiteMenuPlacement placement,
        String labelVi,
        String labelEn,
        String path,
        String iconKey,
        Integer displayOrder,
        Boolean enabled,
        Boolean authRequired,
        Boolean mobilePrimary,
        String featureKey
) {
}
