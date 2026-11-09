package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.response.VocabularyNotebookResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewStateResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyNotebookEntry;

public final class VocabularyNotebookMapper {
    private VocabularyNotebookMapper() {}

    public static VocabularyNotebookResponse toResponse(
            VocabularyNotebookEntry entry,
            Long courseId,
            String lessonTitle,
            String courseTitle,
            String audioUrl,
            VocabularyReviewStateResponse review) {
        Long lessonId = entry.getLesson() == null ? null : entry.getLesson().getId();
        Long masterId =
                entry.getMasterVocabulary() == null ? null : entry.getMasterVocabulary().getId();
        return new VocabularyNotebookResponse(
                entry.getId(),
                lessonId,
                courseId,
                lessonTitle,
                courseTitle,
                entry.getWord(),
                entry.getMeaning(),
                entry.getPronunciation(),
                entry.getExampleSentence(),
                entry.getNote(),
                entry.getCreatedAt(),
                masterId,
                audioUrl,
                review);
    }
}
