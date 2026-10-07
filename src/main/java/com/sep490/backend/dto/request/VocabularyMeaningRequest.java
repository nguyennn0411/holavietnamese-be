package com.sep490.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VocabularyMeaningRequest(

        @NotBlank
        @Size(max = 500)
        String translationEn,

        String definitionEn,

        String usageNote,

        @PositiveOrZero
        Integer displayOrder,

        @Valid
        List<VocabularyExampleRequest> examples
) {
}