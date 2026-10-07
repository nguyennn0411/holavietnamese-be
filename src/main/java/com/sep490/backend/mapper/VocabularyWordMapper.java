package com.sep490.backend.mapper;

import com.sep490.backend.dto.model.VocabularyWord;
import com.sep490.backend.entity.VocabularyWordJpaEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class VocabularyWordMapper {

    private VocabularyWordMapper() {
    }

    public static VocabularyWord toDomain(VocabularyWordJpaEntity entity) {
        return new VocabularyWord(
                entity.getId(),
                entity.getWord(),
                entity.getPronunciation(),
                entity.getPartOfSpeech(),
                entity.getCefrLevel(),
                entity.getAudioUrl(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getArchivedAt(),
                entity.getTopics() == null
                        ? new LinkedHashSet<>()
                        : entity.getTopics()
                          .stream()
                          .map(VocabularyTopicMapper::toDomain)
                          .collect(Collectors.toCollection(LinkedHashSet::new)),
                entity.getMeanings() == null
                        ? List.of()
                        : entity.getMeanings()
                          .stream()
                          .map(VocabularyMeaningMapper::toDomain)
                          .toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}