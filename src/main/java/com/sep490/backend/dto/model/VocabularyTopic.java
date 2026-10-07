package com.sep490.backend.dto.model;

import com.sep490.backend.entity.enums.VocabularyStatus;

public record VocabularyTopic(
        Long id,
        String name,
        String slug,
        String description,
        Integer displayOrder,
        VocabularyStatus status
) {
}