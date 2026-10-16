package com.sep490.backend.dto.response;

import java.util.List;

public record DestinationContentResponse(List<Lesson> lessons, List<Word> vocabulary,
                                         List<String> unavailableIntegrations,
                                         JourneyResponse.Stop journey, String completionBlockedReason,
                                         List<RegionalVocabularyResponse> regionalVariants) {
    public record Lesson(Long id, Long courseId, String title, String titleVi, String courseTitle,
                         boolean published, boolean completed) {}
    public record Word(Long id, String word, String pronunciation, String status) {}
}
