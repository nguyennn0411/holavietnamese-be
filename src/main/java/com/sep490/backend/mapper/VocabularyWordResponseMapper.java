package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyWord;
import com.sep490.backend.dto.response.VocabularyWordResponse;

public final class VocabularyWordResponseMapper {

    private VocabularyWordResponseMapper() {
    }

    public static VocabularyWordResponse toResponse(VocabularyWord word) {
        return toResponse(word, false);
    }

    public static VocabularyWordResponse toPublishedResponse(VocabularyWord word) {
        return toResponse(word, true);
    }

    private static VocabularyWordResponse toResponse(VocabularyWord word, boolean publishedOnly) {
        return new VocabularyWordResponse(
                word.id(),
                word.word(),
                word.pronunciation(),
                word.partOfSpeech(),
                word.cefrLevel(),
                word.audioUrl(),
                word.status(),
                word.publishedAt(),
                word.archivedAt(),
                publishedOnly ? word.topics().stream()
                        .filter(topic -> topic.status() == com.sep490.backend.entity.enums.VocabularyStatus.PUBLISHED)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)) : word.topics(),
                word.meanings(),
                word.createdAt(),
                word.updatedAt()
        );
    }
}
