package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.entity.VocabularyMeaningJpaEntity;

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
                          .toList(),
                entity.getDefinitionVi(),
                com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.labels(entity.getLabels())
        );
    }
}
