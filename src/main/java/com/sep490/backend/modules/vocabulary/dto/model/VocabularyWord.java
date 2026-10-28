package com.sep490.backend.modules.vocabulary.dto.model;

import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import java.time.Instant;
import java.util.List;

public record VocabularyWord(
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
    public VocabularyWord(Long id,String word,String pronunciation,String partOfSpeech,String audioUrl,
            VocabularyStatus status,Instant publishedAt,Instant archivedAt,List<VocabularyMeaning> meanings,
            Instant createdAt,Instant updatedAt) {
        this(id,word,pronunciation,partOfSpeech,audioUrl,status,publishedAt,archivedAt,meanings,createdAt,updatedAt,null,null);
    }
}
