package com.sep490.backend.dto.model;

import com.sep490.backend.entity.enums.VocabularyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record VocabularyWord(
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
        Instant updatedAt,
        DictionaryMetadata.Source source,
        DictionaryMetadata.Lexical lexical
) {
    public VocabularyWord(Long id,String word,String pronunciation,String partOfSpeech,String cefrLevel,String audioUrl,
            VocabularyStatus status,Instant publishedAt,Instant archivedAt,Set<VocabularyTopic> topics,List<VocabularyMeaning> meanings,
            Instant createdAt,Instant updatedAt) {
        this(id,word,pronunciation,partOfSpeech,cefrLevel,audioUrl,status,publishedAt,archivedAt,topics,meanings,createdAt,updatedAt,null,null);
    }
}
