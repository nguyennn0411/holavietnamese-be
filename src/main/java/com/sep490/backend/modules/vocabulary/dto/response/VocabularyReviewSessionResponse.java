package com.sep490.backend.modules.vocabulary.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VocabularyReviewSessionResponse(
        UUID sessionId,
        int notebookCount,
        Instant nextDueAt,
        List<VocabularyNotebookResponse> entries,
        List<VocabularyNotebookResponse> reviewedEntries,
        List<VocabularyReviewResultResponse> results) {}
