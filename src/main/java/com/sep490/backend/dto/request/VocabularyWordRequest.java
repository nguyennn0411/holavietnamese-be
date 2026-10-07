package com.sep490.backend.dto.request;

import com.sep490.backend.entity.enums.VocabularyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public record VocabularyWordRequest(

        @NotBlank
        @Size(max = 200)
        String word,

        @Size(max = 200)
        String pronunciation,

        @NotBlank
        @Size(max = 40)
        String partOfSpeech,

        @NotBlank
        @Size(max = 10)
        String cefrLevel,

        @Size(max = 2048)
        String audioUrl,

        VocabularyStatus status,

        Set<Long> topicIds,

        @Valid
        @NotEmpty
        List<VocabularyMeaningRequest> meanings
) {
}