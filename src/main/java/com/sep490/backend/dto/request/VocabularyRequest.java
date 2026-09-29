package com.sep490.backend.dto.request;

import jakarta.validation.constraints.*;
public record VocabularyRequest(
    @Positive Long lessonId,
    @NotBlank @Size(max = 200) String word,
    @NotBlank @Size(max = 1000) String meaning,
    @Size(max = 200) String pronunciation,
    @Size(max = 2000) String exampleSentence,
    @Size(max = 2000) String note) {}
