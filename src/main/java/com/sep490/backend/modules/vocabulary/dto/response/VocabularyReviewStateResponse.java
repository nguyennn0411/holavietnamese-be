package com.sep490.backend.modules.vocabulary.dto.response;

import java.time.Instant;

public record VocabularyReviewStateResponse(String memoryStatus, int repetitionCount, long intervalMinutes,
        Instant lastReviewedAt, Instant nextReviewAt, boolean due) {}
