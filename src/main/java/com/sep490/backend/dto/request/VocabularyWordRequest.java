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

        @Size(max = 10)
        @jakarta.validation.constraints.Pattern(regexp="A1|A2|B1|B2|C1|C2|",message="CEFR phải là A1–C2 hoặc để trống khi chưa phân loại.")
        String cefrLevel,

        @Size(max = 2048)
        String audioUrl,

        VocabularyStatus status,

        Set<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long> topicIds,

        @Valid
        @NotEmpty
        List<@jakarta.validation.constraints.NotNull VocabularyMeaningRequest> meanings
) {
}
