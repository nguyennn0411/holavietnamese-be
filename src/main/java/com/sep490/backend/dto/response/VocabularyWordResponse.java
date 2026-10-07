package com.sep490.backend.dto.response;

import com.sep490.backend.dto.model.VocabularyMeaning;
import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.entity.enums.VocabularyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record VocabularyWordResponse(
        Long id,
        String word,
        String pronunciation,
        String partOfSpeech,
        String cefrLevel,
        String audioUrl,
        VocabularyStatus status,
        Instant publishedAt,
        Instant archivedAt,
        Set<VocabularyTopic> topics,
        List<VocabularyMeaning> meanings,
        Instant createdAt,
        Instant updatedAt
) {
}