
package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.entity.VocabularyJpaEntity;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyEntry;

public final class VocabularyMapper {

    private VocabularyMapper() {}

    public static VocabularyEntry toDomain(VocabularyJpaEntity e) {
        return new VocabularyEntry(
                e.getId(),
                e.getUser().getId().longValue(),
                e.getLesson() == null ? null : e.getLesson().getId(),
                e.getWord(),
                e.getMeaning(),
                e.getPronunciation(),
                e.getExampleSentence(),
                e.getNote(),
                e.getCreatedAt(),
                e.getMasterVocabulary() == null
                        ? null
                        : e.getMasterVocabulary().getId()
        );
    }
}
