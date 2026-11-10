package com.sep490.backend.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiVocabularyEnrichmentRequest(
        @NotBlank @Size(max = 200) String word,
        @Size(max = 500) String context,
        @Size(max = 200) String lessonTitle
) {}
