package com.sep490.backend.modules.vocabulary.dto.request;

import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VocabularyWordRequest(

        @NotBlank
        @Size(max = 200)
        String word,

        @Size(max = 200)
        String pronunciation,

        @NotBlank
        @Size(max = 40)
        String partOfSpeech,

        @Size(max = 2048)
        String audioUrl,

        VocabularyStatus status,

        @Valid
        @NotEmpty
        List<@jakarta.validation.constraints.NotNull VocabularyMeaningRequest> meanings
) {
}
