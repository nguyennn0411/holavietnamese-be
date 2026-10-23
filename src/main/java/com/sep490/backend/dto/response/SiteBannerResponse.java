package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.SiteBannerActionType;
import com.sep490.backend.entity.enums.SiteBannerPlacement;

import java.time.Instant;

public record SiteBannerResponse(
        Long id,
        String code,
        SiteBannerPlacement placement,
        String eyebrowVi,
        String eyebrowEn,
        String titleVi,
        String titleEn,
        String descriptionVi,
        String descriptionEn,
        String imageUrl,
        String imageAltVi,
        String imageAltEn,
        String photoLabelVi,
        String photoLabelEn,
        SiteBannerActionType actionType,
        String actionLabelVi,
        String actionLabelEn,
        String actionTarget,
        Integer displayOrder,
        Boolean enabled,
        Instant startsAt,
        Instant endsAt,
        String featureKey
) {
}
