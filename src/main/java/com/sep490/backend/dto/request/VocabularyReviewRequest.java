package com.sep490.backend.dto.request;

import com.sep490.backend.entity.enums.VocabularyReviewRating;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VocabularyReviewRequest(@NotNull UUID sessionId, @NotNull VocabularyReviewRating rating) {}
