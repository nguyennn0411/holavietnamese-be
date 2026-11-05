package com.sep490.backend.modules.explore.regional.dto.response;

import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import java.time.Instant;
import java.util.List;

public record RegionalVocabularyResponse(Long id, Long vocabularyId, String masterWord,
        VietnamRegion region, String word, String meaning, String meaningEn, String pronunciation, String audioUrl,
        String exampleSentence, String exampleSentenceEn, String usageNote, String usageNoteEn,
        VocabularyStatus status, Instant createdAt, Instant updatedAt) {
    public record Comparison(Long vocabularyId, String word, List<RegionalVocabularyResponse> variants) {}
}
