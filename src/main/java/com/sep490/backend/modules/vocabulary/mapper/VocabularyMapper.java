package com.sep490.backend.modules.vocabulary.mapper;

import com.sep490.backend.modules.vocabulary.dto.request.VocabularyExampleRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.response.DictionaryMetadata;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyExampleResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyMeaningResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyExample;
import com.sep490.backend.modules.vocabulary.entity.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;

import java.util.List;

public final class VocabularyMapper {
    private VocabularyMapper() {}

    public static VocabularyResponse toResponse(VocabularyWord entity) {
        return new VocabularyResponse(
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
                        : entity.getMeanings().stream().map(VocabularyMapper::toResponse).toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                DictionaryMetadata.decode(
                        entity.getSourceMetadata(), DictionaryMetadata.Source.class),
                DictionaryMetadata.decode(
                        entity.getLexicalData(), DictionaryMetadata.Lexical.class));
    }

    public static VocabularyMeaningResponse toResponse(VocabularyMeaning entity) {
        return new VocabularyMeaningResponse(
                entity.getId(),
                entity.getTranslationEn(),
                entity.getDefinitionEn(),
                entity.getUsageNote(),
                entity.getDisplayOrder(),
                entity.getExamples() == null
                        ? List.of()
                        : entity.getExamples().stream().map(VocabularyMapper::toResponse).toList(),
                entity.getDefinitionVi(),
                DictionaryMetadata.labels(entity.getLabels()));
    }

    public static VocabularyExampleResponse toResponse(VocabularyExample entity) {
        return new VocabularyExampleResponse(
                entity.getId(),
                entity.getExampleVi(),
                entity.getTranslationEn(),
                entity.getAudioUrl(),
                entity.getDisplayOrder());
    }

    public static void updateEntity(VocabularyWord entity, VocabularyResponse word) {
        entity.setWord(word.word());
        entity.setSearchWord(VocabularyText.searchKey(word.word()));
        entity.setPronunciation(word.pronunciation());
        entity.setPartOfSpeech(word.partOfSpeech());
        entity.setAudioUrl(word.audioUrl());
        entity.setStatus(word.status());
        entity.setPublishedAt(word.publishedAt());
        entity.setArchivedAt(word.archivedAt());

        replaceMeanings(entity, word.meanings());
    }

    private static void replaceMeanings(
            VocabularyWord entity, List<VocabularyMeaningResponse> meanings) {
        entity.getMeanings().clear();

        if (meanings == null || meanings.isEmpty()) {
            return;
        }

        for (VocabularyMeaningResponse meaning : meanings) {
            VocabularyMeaning meaningEntity = new VocabularyMeaning();

            meaningEntity.setVocabulary(entity);
            meaningEntity.setTranslationEn(meaning.translationEn());
            meaningEntity.setDefinitionVi(meaning.definitionVi());
            meaningEntity.setLabels(DictionaryMetadata.encode(meaning.labels()));
            meaningEntity.setDefinitionEn(meaning.definitionEn());
            meaningEntity.setUsageNote(meaning.usageNote());
            meaningEntity.setDisplayOrder(meaning.displayOrder());

            if (meaning.examples() != null) {
                for (VocabularyExampleResponse example : meaning.examples()) {
                    VocabularyExample exampleEntity = new VocabularyExample();

                    exampleEntity.setVocabularyMeaning(meaningEntity);
                    exampleEntity.setExampleVi(example.exampleVi());
                    exampleEntity.setTranslationEn(example.translationEn());
                    exampleEntity.setAudioUrl(example.audioUrl());
                    exampleEntity.setDisplayOrder(example.displayOrder());

                    meaningEntity.getExamples().add(exampleEntity);
                }
            }

            entity.getMeanings().add(meaningEntity);
        }
    }

    public static List<VocabularyMeaningResponse> mapMeanings(
            List<VocabularyMeaningRequest> requests) {
        return requests.stream().map(VocabularyMapper::mapMeaning).toList();
    }

    private static VocabularyMeaningResponse mapMeaning(VocabularyMeaningRequest request) {
        List<VocabularyExampleResponse> examples =
                request.examples() == null
                        ? List.of()
                        : request.examples().stream().map(VocabularyMapper::mapExample).toList();

        return new VocabularyMeaningResponse(
                null,
                trimToNull(request.translationEn()),
                trimToNull(request.definitionEn()),
                trimToNull(request.usageNote()),
                request.displayOrder() == null ? 0 : request.displayOrder(),
                examples,
                trimToNull(request.definitionVi()),
                request.labels() == null ? List.of() : request.labels());
    }

    private static VocabularyExampleResponse mapExample(VocabularyExampleRequest request) {
        return new VocabularyExampleResponse(
                null,
                request.exampleVi().trim(),
                trimToNull(request.translationEn()),
                trimToNull(request.audioUrl()),
                request.displayOrder() == null ? 0 : request.displayOrder());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
