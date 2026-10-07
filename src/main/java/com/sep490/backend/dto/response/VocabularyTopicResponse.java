package com.sep490.backend.dto.response;

import com.sep490.backend.entity.enums.VocabularyStatus;

public record VocabularyTopicResponse(
        Long id,
        String name,
        String slug,
        String description,
        Integer displayOrder,
        VocabularyStatus status
) {
}