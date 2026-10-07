package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyExample;
import com.sep490.backend.entity.VocabularyExampleJpaEntity;

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