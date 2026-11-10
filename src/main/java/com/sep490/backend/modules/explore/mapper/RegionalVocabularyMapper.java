package com.sep490.backend.modules.explore.mapper;

import com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;

public final class RegionalVocabularyMapper {
    private RegionalVocabularyMapper() {}

    public static RegionalVocabularyResponse toResponse(RegionalVocabulary e) {
        return new RegionalVocabularyResponse(
                e.getId(),
                e.getVocabulary().getId(),
                e.getVocabulary().getWord(),
                e.getRegion(),
                e.getWord(),
                e.getMeaning(),
                e.getMeaningEn(),
                e.getPronunciation(),
                e.getAudioUrl(),
                e.getExampleSentence(),
                e.getExampleSentenceEn(),
                e.getUsageNote(),
                e.getUsageNoteEn(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
