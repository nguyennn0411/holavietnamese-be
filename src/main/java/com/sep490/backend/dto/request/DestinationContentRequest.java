package com.sep490.backend.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record DestinationContentRequest(
        @NotNull @Size(max = 500) List<@NotNull @Positive Long> lessonIds,
        @NotNull @Size(max = 500) List<@NotNull @Positive Long> vocabularyIds) {}
