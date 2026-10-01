package com.sep490.backend.dto.response;

import java.time.Instant;
public record VocabularyResponse(Long id, Long lessonId, Long courseId, String lessonTitle, String courseTitle,
    String word, String meaning, String pronunciation, String exampleSentence, String note, Instant createdAt) {}
