package com.sep490.backend.mapper;

import com.sep490.backend.entity.VocabularyJpaEntity;
import com.sep490.backend.dto.model.VocabularyEntry;
public final class VocabularyMapper {
    private VocabularyMapper() {}
    public static VocabularyEntry toDomain(VocabularyJpaEntity e) { return new VocabularyEntry(e.getId(), e.getUser().getId(), e.getLesson() == null ? null : e.getLesson().getId(), e.getWord(), e.getMeaning(), e.getPronunciation(), e.getExampleSentence(), e.getNote(), e.getCreatedAt()); }
}
