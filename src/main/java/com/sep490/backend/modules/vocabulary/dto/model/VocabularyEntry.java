
package com.sep490.backend.modules.vocabulary.dto.model;

import java.time.Instant;

public record VocabularyEntry(
        Long id,
        Long userId,
        Long lessonId,
        String word,
        String meaning,
        String pronunciation,
        String exampleSentence,
        String note,
        Instant createdAt,
        Long masterVocabularyId
) {
    public VocabularyEntry(
            Long id,
            Long userId,
            Long lessonId,
            String word,
            String meaning,
            String pronunciation,
            String exampleSentence,
            String note,
            Instant createdAt
    ) {
        this(
                id,
                userId,
                lessonId,
                word,
                meaning,
                pronunciation,
                exampleSentence,
                note,
                createdAt,
                null
        );
    }
}
