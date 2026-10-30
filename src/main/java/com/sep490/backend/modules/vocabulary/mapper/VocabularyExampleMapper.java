package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyExample;
import com.sep490.backend.modules.vocabulary.entity.VocabularyExampleJpaEntity;

public final class VocabularyExampleMapper {

    private VocabularyExampleMapper() {
    }

    public static VocabularyExample toDomain(VocabularyExampleJpaEntity entity) {
        return new VocabularyExample(
                entity.getId(),
                entity.getExampleVi(),
                entity.getTranslationEn(),
                entity.getAudioUrl(),
                entity.getDisplayOrder()
        );
    }
}
