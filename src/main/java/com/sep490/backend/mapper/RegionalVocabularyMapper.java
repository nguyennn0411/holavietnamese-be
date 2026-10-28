package com.sep490.backend.mapper;

import com.sep490.backend.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.entity.RegionalVocabularyJpaEntity;

public final class RegionalVocabularyMapper {
    private RegionalVocabularyMapper() {}
    public static RegionalVocabularyResponse toResponse(RegionalVocabularyJpaEntity e) {
        return new RegionalVocabularyResponse(e.getId(), e.getVocabulary().getId(), e.getVocabulary().getWord(),
                e.getRegion(), e.getWord(), e.getMeaning(), e.getPronunciation(), e.getAudioUrl(),
                e.getExampleSentence(), e.getUsageNote(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
