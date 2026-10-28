package com.sep490.backend.modules.explore.regional.dto.request;

import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import jakarta.validation.constraints.*;

public record RegionalVocabularyRequest(
        @NotNull @Positive Long vocabularyId,
        @NotNull VietnamRegion region,
        @NotBlank @Size(max = 200) String word,
        @NotBlank @Size(max = 1000) String meaning,
        @Size(max = 200) String pronunciation,
        @Size(max = 2048) String audioUrl,
        @Size(max = 2000) String exampleSentence,
        @Size(max = 2000) String usageNote,
        VocabularyStatus status) {}
