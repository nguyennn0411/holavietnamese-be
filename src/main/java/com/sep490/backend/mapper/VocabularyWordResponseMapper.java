package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyWord;
import com.sep490.backend.dto.response.VocabularyWordResponse;

public final class VocabularyWordResponseMapper {

    private VocabularyWordResponseMapper() {
    }

    public static VocabularyWordResponse toResponse(VocabularyWord word) {
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
                word.topics(),
                word.meanings(),
                word.createdAt(),
                word.updatedAt()
        );
    }
}