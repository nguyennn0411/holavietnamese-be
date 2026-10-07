package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.entity.VocabularyTopicJpaEntity;

public final class VocabularyTopicMapper {

    private VocabularyTopicMapper() {
    }

    public static VocabularyTopic toDomain(VocabularyTopicJpaEntity entity) {
        return new VocabularyTopic(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getDisplayOrder(),
                entity.getStatus()
        );
    }
}