package com.sep490.backend.modules.culture.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record DestinationExploreContentRequest(
        @NotNull @Size(max = 500) List<@NotNull @Positive Long> vocabularyIds,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9_-]+") String> cultureIds,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> scenarioIds
) {}
