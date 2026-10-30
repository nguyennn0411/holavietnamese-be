package com.sep490.backend.modules.explore.destination.mapper;

import com.sep490.backend.modules.explore.destination.dto.model.Destination;
import com.sep490.backend.modules.explore.destination.entity.DestinationJpaEntity;

public final class DestinationMapper {

    private DestinationMapper() {
    }

    public static Destination toDomain(DestinationJpaEntity entity) {
        return new Destination(
                entity.getId(),
                entity.getNameVi(),
                entity.getNameEn(),
                entity.getSlug(),
                entity.getRegion(),
                entity.getShortDescriptionVi(),
                entity.getShortDescriptionEn(),
                entity.getDescriptionVi(),
                entity.getDescriptionEn(),
                entity.getImageUrl(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getArchivedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
