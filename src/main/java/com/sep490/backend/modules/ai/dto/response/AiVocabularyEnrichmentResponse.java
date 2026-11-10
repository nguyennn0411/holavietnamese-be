package com.sep490.backend.modules.ai.dto.response;

public record AiVocabularyEnrichmentResponse(
        String word,
        String meaning,
        String pronunciation,
        String exampleSentence,
        String note
) {}
