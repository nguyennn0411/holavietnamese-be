package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.VocabularyReviewRating;
import java.time.Instant;

public record VocabularyReviewResultResponse(Long entryId, String word, VocabularyReviewRating rating,
        Instant reviewedAt, Instant nextReviewAt, long intervalMinutes) {}
