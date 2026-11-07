package com.sep490.backend.modules.explore.destination.dto.response;

public record DestinationManagementResponse(
        DestinationResponse destination,
        DestinationExploreContentResponse exploreContent
) {}
