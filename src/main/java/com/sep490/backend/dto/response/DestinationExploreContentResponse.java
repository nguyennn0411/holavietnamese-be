package com.sep490.backend.dto.response;

import java.util.List;

public record DestinationExploreContentResponse(
        List<Word> vocabulary,
        List<RegionalVocabularyResponse> regionalVariants,
        List<String> cultureIds,
        List<Scenario> scenarios
) {
    public record Word(Long id, String word, String pronunciation, String status) {}
    public record Scenario(String id, String title, String level, String overview, String thumbnail, String status) {}
}
