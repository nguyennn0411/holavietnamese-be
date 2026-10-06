package com.sep490.backend.dto.model;

import java.time.Instant;
public record VocabularyEntry(Long id, Integer userId, Long lessonId, String word, String meaning,
                              String pronunciation, String exampleSentence, String note, Instant createdAt) {}
