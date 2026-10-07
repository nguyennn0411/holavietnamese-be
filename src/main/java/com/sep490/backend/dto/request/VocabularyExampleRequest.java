package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VocabularyExampleRequest(

        @NotBlank
        String exampleVi,

        @NotBlank
        String translationEn,

        @Size(max = 2048)
        String audioUrl,

        @PositiveOrZero
        Integer displayOrder
) {
}