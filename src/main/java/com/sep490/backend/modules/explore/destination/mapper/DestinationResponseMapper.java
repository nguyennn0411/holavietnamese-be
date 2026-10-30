package com.sep490.backend.modules.explore.destination.mapper;

import com.sep490.backend.modules.explore.destination.dto.model.Destination;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationResponse;

public final class DestinationResponseMapper {

    private DestinationResponseMapper() {
    }

    public static DestinationResponse toResponse(Destination destination) {
        return new DestinationResponse(
                destination.id(),
                destination.nameVi(),
                destination.nameEn(),
                destination.slug(),
                destination.region(),
                destination.shortDescriptionVi(),
                destination.shortDescriptionEn(),
                destination.descriptionVi(),
                destination.descriptionEn(),
                destination.imageUrl(),
                destination.status(),
                destination.publishedAt(),
                destination.archivedAt(),
                destination.createdAt(),
                destination.updatedAt()
        );
    }
}
