package com.sep490.backend.modules.culture.mapper;

import com.sep490.backend.modules.culture.dto.model.Destination;
import com.sep490.backend.modules.culture.entity.DestinationJpaEntity;

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
