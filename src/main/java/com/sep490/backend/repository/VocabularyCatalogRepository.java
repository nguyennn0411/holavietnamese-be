package com.sep490.backend.repository;

import com.sep490.backend.dto.model.VocabularyExample;
import com.sep490.backend.dto.model.VocabularyMeaning;
import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.dto.model.VocabularyWord;
import com.sep490.backend.entity.VocabularyExampleJpaEntity;
import com.sep490.backend.entity.VocabularyMeaningJpaEntity;
import com.sep490.backend.entity.VocabularyTopicJpaEntity;
import com.sep490.backend.entity.VocabularyWordJpaEntity;
import com.sep490.backend.mapper.VocabularyWordMapper;
import com.sep490.backend.repository.jpa.VocabularyTopicJpaRepository;
import com.sep490.backend.repository.jpa.VocabularyWordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.sep490.backend.entity.enums.VocabularyStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class VocabularyCatalogRepository {

    private final VocabularyWordJpaRepository repository;
    private final VocabularyTopicJpaRepository topicRepository;

    public List<VocabularyWord> findAll() {
        return repository.findAll()
                .stream()
                .map(VocabularyWordMapper::toDomain)
                .toList();
    }

    public Optional<VocabularyWord> findById(Long id) {
        return repository.findById(id)
                .map(VocabularyWordMapper::toDomain);
    }

    public VocabularyWord save(VocabularyWord word) {
        VocabularyWordJpaEntity entity;

        if (word.id() == null) {
            entity = new VocabularyWordJpaEntity();
        } else {
            entity = repository.findById(word.id())
                    .orElseThrow();
        }

        entity.setWord(word.word());
        entity.setPronunciation(word.pronunciation());
        entity.setPartOfSpeech(word.partOfSpeech());
        entity.setCefrLevel(word.cefrLevel());
        entity.setAudioUrl(word.audioUrl());
        entity.setStatus(word.status());
        entity.setPublishedAt(word.publishedAt());
        entity.setArchivedAt(word.archivedAt());

        replaceTopics(entity, word.topics());
        replaceMeanings(entity, word.meanings());

        return VocabularyWordMapper.toDomain(
                repository.saveAndFlush(entity)
        );
    }

    public long count() {
        return repository.count();
    }

    private void replaceTopics(
            VocabularyWordJpaEntity entity,
            Set<VocabularyTopic> topics
    ) {
        entity.getTopics().clear();

        if (topics == null || topics.isEmpty()) {
            return;
        }

        List<Long> topicIds = topics.stream()
                .map(VocabularyTopic::id)
                .toList();

        List<VocabularyTopicJpaEntity> topicEntities =
                topicRepository.findAllById(topicIds);

        entity.getTopics().addAll(topicEntities);
    }

    private void replaceMeanings(
            VocabularyWordJpaEntity entity,
            List<VocabularyMeaning> meanings
    ) {
        entity.getMeanings().clear();

        if (meanings == null || meanings.isEmpty()) {
            return;
        }

        for (VocabularyMeaning meaning : meanings) {
            VocabularyMeaningJpaEntity meaningEntity =
                    new VocabularyMeaningJpaEntity();

            meaningEntity.setVocabulary(entity);
            meaningEntity.setTranslationEn(meaning.translationEn());
            meaningEntity.setDefinitionEn(meaning.definitionEn());
            meaningEntity.setUsageNote(meaning.usageNote());
            meaningEntity.setDisplayOrder(meaning.displayOrder());

            if (meaning.examples() != null) {
                for (VocabularyExample example : meaning.examples()) {
                    VocabularyExampleJpaEntity exampleEntity =
                            new VocabularyExampleJpaEntity();

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

    public List<VocabularyWord> search(
            String keyword,
            VocabularyStatus status,
            String cefrLevel,
            String partOfSpeech,
            Long topicId
    ) {
        Specification<VocabularyWordJpaEntity> specification =
                (root, query, criteriaBuilder) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (keyword != null && !keyword.isBlank()) {
                        String normalizedKeyword =
                                "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

                        predicates.add(
                                criteriaBuilder.like(
                                        criteriaBuilder.lower(root.get("word")),
                                        normalizedKeyword
                                )
                        );
                    }

                    if (status != null) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        root.get("status"),
                                        status
                                )
                        );
                    }

                    if (cefrLevel != null && !cefrLevel.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        criteriaBuilder.lower(root.get("cefrLevel")),
                                        cefrLevel.trim().toLowerCase(Locale.ROOT)
                                )
                        );
                    }

                    if (partOfSpeech != null && !partOfSpeech.isBlank()) {
                        predicates.add(
                                criteriaBuilder.equal(
                                        criteriaBuilder.lower(root.get("partOfSpeech")),
                                        partOfSpeech.trim().toLowerCase(Locale.ROOT)
                                )
                        );
                    }

                    if (topicId != null) {
                        query.distinct(true);

                        predicates.add(
                                criteriaBuilder.equal(
                                        root.join("topics", JoinType.INNER)
                                                .get("id"),
                                        topicId
                                )
                        );
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(Predicate[]::new)
                    );
                };

        return repository.findAll(
                        specification,
                        Sort.by(
                                Sort.Order.asc("word"),
                                Sort.Order.asc("id")
                        )
                )
                .stream()
                .map(VocabularyWordMapper::toDomain)
                .toList();
    }
}