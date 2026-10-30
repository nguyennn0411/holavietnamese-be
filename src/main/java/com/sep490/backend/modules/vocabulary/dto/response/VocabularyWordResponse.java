package com.sep490.backend.modules.vocabulary.dto.response;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import java.time.Instant;
import java.util.List;

public record VocabularyWordResponse(
        Long id,
        String word,
        String pronunciation,
        String partOfSpeech,
        String audioUrl,
        VocabularyStatus status,
        Instant publishedAt,
        Instant archivedAt,
        List<VocabularyMeaning> meanings,
        Instant createdAt,
        Instant updatedAt,
        DictionaryMetadata.Source source,
        DictionaryMetadata.Lexical lexical
) {
}
