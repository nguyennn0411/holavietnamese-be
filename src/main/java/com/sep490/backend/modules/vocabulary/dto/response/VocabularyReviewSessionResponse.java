package com.sep490.backend.modules.vocabulary.dto.response;

import java.util.List;
import java.util.UUID;

public record VocabularyReviewSessionResponse(
        UUID sessionId,
        int notebookCount,
        List<VocabularyNotebookResponse> entries,
        List<VocabularyReviewResultResponse> results) {}
