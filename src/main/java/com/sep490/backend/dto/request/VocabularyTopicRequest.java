package com.sep490.backend.dto.request;

import com.sep490.backend.entity.enums.VocabularyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VocabularyTopicRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 120)
        String slug,

        @Size(max = 500)
        String description,

        @PositiveOrZero
        Integer displayOrder,

        VocabularyStatus status
) {
}