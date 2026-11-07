package com.sep490.backend.modules.explore.destination.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DestinationManagementRequest(
        @Valid @NotNull DestinationRequest destination,
        @Valid @NotNull DestinationExploreContentRequest exploreContent
) {}
