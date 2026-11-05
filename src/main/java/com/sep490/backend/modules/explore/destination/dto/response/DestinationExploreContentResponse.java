package com.sep490.backend.modules.explore.destination.dto.response;

import com.sep490.backend.modules.explore.regional.dto.response.RegionalVocabularyResponse;
import java.util.List;

public record DestinationExploreContentResponse(
        List<Word> vocabulary,
        List<RegionalVocabularyResponse> regionalVariants,
        List<String> cultureIds,
        List<Scenario> scenarios
) {
    public record Word(Long id, String word, String pronunciation, String status) {}
    public record Scenario(String id, String title, String titleVi, String titleEn, String level,
                           String overview, String overviewVi, String overviewEn,
                           String thumbnail, String status) {}
}
