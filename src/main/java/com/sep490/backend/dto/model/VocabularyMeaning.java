package com.sep490.backend.dto.model;

import java.util.List;

public record VocabularyMeaning(
        Long id,
        String translationEn,
        String definitionEn,
        String usageNote,
        Integer displayOrder,
        List<VocabularyExample> examples
) {
}