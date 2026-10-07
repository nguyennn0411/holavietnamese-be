package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyMeaning;
import com.sep490.backend.entity.VocabularyMeaningJpaEntity;

import java.util.List;

public final class VocabularyMeaningMapper {

    private VocabularyMeaningMapper() {
    }

    public static VocabularyMeaning toDomain(VocabularyMeaningJpaEntity entity) {
        return new VocabularyMeaning(
                entity.getId(),
                entity.getTranslationEn(),
                entity.getDefinitionEn(),
                entity.getUsageNote(),
                entity.getDisplayOrder(),
                entity.getExamples() == null
                        ? List.of()
                        : entity.getExamples()
                          .stream()
                          .map(VocabularyExampleMapper::toDomain)
                          .toList()
        );
    }
}