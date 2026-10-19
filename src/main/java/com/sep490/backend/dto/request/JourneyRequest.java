package com.sep490.backend.dto.request;

import com.sep490.backend.entity.enums.DestinationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record JourneyRequest(@NotNull @Size(max = 500) List<@NotNull @Valid Stop> stops,
        @Pattern(regexp = "PREVIOUS_COMPLETED") String unlockRule,
        @Pattern(regexp = "ALL_LINKED_LESSONS") String completionRule) {
    public JourneyRequest(List<Stop> stops) { this(stops, "PREVIOUS_COMPLETED", "ALL_LINKED_LESSONS"); }
    public record Stop(@NotNull @Positive Long destinationId, @NotNull DestinationStatus status) {}
}
