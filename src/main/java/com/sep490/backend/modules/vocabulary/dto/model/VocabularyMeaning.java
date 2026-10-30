package com.sep490.backend.modules.vocabulary.dto.model;

import java.util.List;

public record VocabularyMeaning(
        Long id,
        String translationEn,
        String definitionEn,
        String usageNote,
        Integer displayOrder,
        List<VocabularyExample> examples,
        String definitionVi,
        List<String> labels
) {
    public VocabularyMeaning(Long id,String translationEn,String definitionEn,String usageNote,Integer displayOrder,List<VocabularyExample> examples) {
        this(id,translationEn,definitionEn,usageNote,displayOrder,examples,null,List.of());
    }
}
