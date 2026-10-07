package com.sep490.backend.dto.model;

public record VocabularyExample(
        Long id,
        String exampleVi,
        String translationEn,
        String audioUrl,
        Integer displayOrder
) {
}