package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.DestinationStatus;
import com.sep490.backend.entity.enums.VietnamRegion;

import java.time.Instant;

public record DestinationResponse(
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