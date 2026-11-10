package com.sep490.backend.modules.vocabulary.dto.response;

import java.util.List;

public record VocabularyMeaningResponse(
        Long id,
        String translationEn,
        String definitionEn,
        String usageNote,
        Integer displayOrder,
        List<VocabularyExampleResponse> examples,
        String definitionVi,
        List<String> labels) {
    public VocabularyMeaningResponse(
            Long id,
            String translationEn,
            String definitionEn,
            String usageNote,
            Integer displayOrder,
            List<VocabularyExampleResponse> examples) {
        this(id, translationEn, definitionEn, usageNote, displayOrder, examples, null, List.of());
    }
}
