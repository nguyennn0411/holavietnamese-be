package com.sep490.backend.modules.vocabulary.dto.response;

import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import java.time.Instant;
import java.util.List;

public record VocabularyResponse(
        Long id,
        String word,
        String pronunciation,
        String partOfSpeech,
        String audioUrl,
        VocabularyStatus status,
        Instant publishedAt,
        Instant archivedAt,
        List<VocabularyMeaningResponse> meanings,
        Instant createdAt,
        Instant updatedAt,
        DictionaryMetadata.Source source,
        DictionaryMetadata.Lexical lexical) {
    public VocabularyResponse(
            Long id,
            String word,
            String pronunciation,
            String partOfSpeech,
            String audioUrl,
            VocabularyStatus status,
            Instant publishedAt,
            Instant archivedAt,
            List<VocabularyMeaningResponse> meanings,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                word,
                pronunciation,
                partOfSpeech,
                audioUrl,
                status,
                publishedAt,
                archivedAt,
                meanings,
                createdAt,
                updatedAt,
                null,
                null);
    }
}
