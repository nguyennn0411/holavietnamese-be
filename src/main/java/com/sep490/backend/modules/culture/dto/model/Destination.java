package com.sep490.backend.modules.culture.dto.model;

import com.sep490.backend.modules.culture.entity.enums.DestinationStatus;
import com.sep490.backend.modules.culture.entity.enums.VietnamRegion;

import java.time.Instant;

public record Destination(
        Long id,
        String nameVi,
        String nameEn,
        String slug,
        VietnamRegion region,
        String shortDescriptionVi,
        String shortDescriptionEn,
        String descriptionVi,
        String descriptionEn,
        String imageUrl,
        DestinationStatus status,
        Instant publishedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
