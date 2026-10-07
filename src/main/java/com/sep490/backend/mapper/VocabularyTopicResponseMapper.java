package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.dto.response.VocabularyTopicResponse;

public final class VocabularyTopicResponseMapper {

    private VocabularyTopicResponseMapper() {
    }

    public static VocabularyTopicResponse toResponse(VocabularyTopic topic) {
        return new VocabularyTopicResponse(
                topic.id(),
                topic.name(),
                topic.slug(),
                topic.description(),
                topic.displayOrder(),
                topic.status()
        );
    }
}