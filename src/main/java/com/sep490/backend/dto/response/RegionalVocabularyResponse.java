package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.*;
import java.time.Instant;
import java.util.List;

public record RegionalVocabularyResponse(Long id, Long vocabularyId, String masterWord,
        VietnamRegion region, String word, String meaning, String pronunciation, String audioUrl,
        String exampleSentence, String usageNote, VocabularyStatus status, Instant createdAt, Instant updatedAt) {
    public record Comparison(Long vocabularyId, String word, List<RegionalVocabularyResponse> variants) {}
}
