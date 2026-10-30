package com.sep490.backend.modules.vocabulary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VocabularyExampleRequest(

        @NotBlank
        String exampleVi,

        String translationEn,

        @Size(max = 2048)
        String audioUrl,

        @PositiveOrZero
        Integer displayOrder
) {
}
