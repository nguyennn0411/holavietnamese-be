package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;

import java.util.List;

public final class VocabularyWordMapper {

    private VocabularyWordMapper() {
    }

    public static VocabularyWord toDomain(VocabularyWordJpaEntity entity) {
        return new VocabularyWord(
                entity.getId(),
                entity.getWord(),
                entity.getPronunciation(),
                entity.getPartOfSpeech(),
                entity.getAudioUrl(),
                entity.getStatus(),
                entity.getPublishedAt(),
                entity.getArchivedAt(),
                entity.getMeanings() == null
                        ? List.of()
                        : entity.getMeanings()
                          .stream()
                          .map(VocabularyMeaningMapper::toDomain)
                          .toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.decode(entity.getSourceMetadata(),com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.Source.class),
                com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.decode(entity.getLexicalData(),com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.Lexical.class)
        );
    }
}
