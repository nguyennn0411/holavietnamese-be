package com.sep490.backend.modules.vocabulary.dto.response;

public record VocabularyExampleResponse(
        Long id, String exampleVi, String translationEn, String audioUrl, Integer displayOrder) {}
