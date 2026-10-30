package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyWord;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyWordResponse;

public final class VocabularyWordResponseMapper {

    private VocabularyWordResponseMapper() {
    }

    public static VocabularyWordResponse toResponse(VocabularyWord word) {
        return new VocabularyWordResponse(
                word.id(),
                word.word(),
                word.pronunciation(),
                word.partOfSpeech(),
                word.audioUrl(),
                word.status(),
                word.publishedAt(),
                word.archivedAt(),
                word.meanings(),
                word.createdAt(),
                word.updatedAt(),word.source(),word.lexical()
        );
    }

    public static VocabularyWordResponse toPublishedResponse(VocabularyWord word) {
        return toResponse(word);
    }
}
