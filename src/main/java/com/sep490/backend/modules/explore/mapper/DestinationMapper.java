package com.sep490.backend.modules.explore.mapper;

import com.sep490.backend.modules.ai.entity.AiScenario;
import com.sep490.backend.modules.explore.dto.response.DestinationExploreContentResponse;
import com.sep490.backend.modules.explore.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.Destination;

public final class DestinationMapper {

    private DestinationMapper() {}

    public static DestinationResponse toResponse(Destination entity) {
        return new DestinationResponse(
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
                entity.getUpdatedAt());
    }

    public static void updateEntity(Destination entity, DestinationResponse destination) {
        entity.setNameVi(destination.nameVi());
        entity.setNameEn(destination.nameEn());
        entity.setSlug(destination.slug());
        entity.setRegion(destination.region());

        entity.setShortDescriptionVi(destination.shortDescriptionVi());
        entity.setShortDescriptionEn(destination.shortDescriptionEn());

        entity.setDescriptionVi(destination.descriptionVi());
        entity.setDescriptionEn(destination.descriptionEn());
        entity.setImageUrl(destination.imageUrl());

        entity.setStatus(destination.status());
        entity.setPublishedAt(destination.publishedAt());
        entity.setArchivedAt(destination.archivedAt());
    }

    /** Chuyen kich ban da loc sang du lieu hien thi, giu quy tac du phong ngon ngu. */
    public static DestinationExploreContentResponse.Scenario toScenarioResponse(
            AiScenario scenario) {
        String titleVi =
                scenario.getTitleVi() == null ? scenario.getTitle() : scenario.getTitleVi();
        String overviewVi =
                scenario.getOverviewVi() == null
                        ? scenario.getOverview()
                        : scenario.getOverviewVi();
        return new DestinationExploreContentResponse.Scenario(
                scenario.getId(),
                scenario.getTitle(),
                titleVi,
                scenario.getTitleEn(),
                scenario.getLevel(),
                scenario.getOverview(),
                overviewVi,
                scenario.getOverviewEn(),
                scenario.getThumbnail(),
                scenario.getStatus());
    }
}
