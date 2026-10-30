
package com.sep490.backend.modules.vocabulary.dto.response;

import java.time.Instant;

public record VocabularyResponse(
        Long id,
        Long lessonId,
        Long courseId,
        String lessonTitle,
        String courseTitle,
        String word,
        String meaning,
        String pronunciation,
        String exampleSentence,
        String note,
        Instant createdAt,
        Long masterVocabularyId,
        String audioUrl,
        VocabularyReviewStateResponse review
) {
    public VocabularyResponse(
            Long id,
            Long lessonId,
            Long courseId,
            String lessonTitle,
            String courseTitle,
            String word,
            String meaning,
            String pronunciation,
            String exampleSentence,
            String note,
            Instant createdAt
    ) {
        this(
                id,
                lessonId,
                courseId,
                lessonTitle,
                courseTitle,
                word,
                meaning,
                pronunciation,
                exampleSentence,
                note,
                createdAt,
                null,
                null,
                null
        );
    }

    public VocabularyResponse(Long id, Long lessonId, Long courseId, String lessonTitle, String courseTitle,
            String word, String meaning, String pronunciation, String exampleSentence, String note,
            Instant createdAt, Long masterVocabularyId) {
        this(id, lessonId, courseId, lessonTitle, courseTitle, word, meaning, pronunciation,
                exampleSentence, note, createdAt, masterVocabularyId, null, null);
    }
}
