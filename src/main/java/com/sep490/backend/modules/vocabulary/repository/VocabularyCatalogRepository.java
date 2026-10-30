package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyExample;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.VocabularyExampleJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyMeaningJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyWordMapper;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyWordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class VocabularyCatalogRepository {

    private final VocabularyWordJpaRepository repository;

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
        entity.setSearchWord(com.sep490.backend.modules.vocabulary.service.VocabularyText.searchKey(word.word()));
        entity.setPronunciation(word.pronunciation());
        entity.setPartOfSpeech(word.partOfSpeech());
        entity.setAudioUrl(word.audioUrl());
        entity.setStatus(word.status());
        entity.setPublishedAt(word.publishedAt());
        entity.setArchivedAt(word.archivedAt());

        replaceMeanings(entity, word.meanings());

        return VocabularyWordMapper.toDomain(
                repository.saveAndFlush(entity)
        );
    }

    public long count() {
        return repository.count();
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
            meaningEntity.setDefinitionVi(meaning.definitionVi());
            meaningEntity.setLabels(com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.encode(meaning.labels()));
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

    public List<VocabularyWord> search(String keyword, VocabularyStatus status, String part) {
        return repository.findAll(specification(keyword,status,part,false),
                        org.springframework.data.domain.PageRequest.of(0,500))
                .getContent().stream().map(VocabularyWordMapper::toDomain).toList();
    }
    public org.springframework.data.domain.Page<VocabularyWord> searchPage(String keyword,VocabularyStatus status,
            String part,int page,int size,boolean exact) {
        return repository.findAll(specification(keyword,status,part,exact),
                org.springframework.data.domain.PageRequest.of(Math.max(0,page),Math.max(1,Math.min(size,100))))
                .map(VocabularyWordMapper::toDomain);
    }
    private Specification<VocabularyWordJpaEntity> specification(String keyword,VocabularyStatus status,String part,boolean exact) {
        String text = keyword == null ? "" : com.sep490.backend.modules.vocabulary.service.VocabularyText.normalize(keyword).toLowerCase(Locale.ROOT);
        String folded = com.sep490.backend.modules.vocabulary.service.VocabularyText.searchKey(text);
        return (root,query,cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"),status));
            if (part != null && !part.isBlank()) predicates.add(cb.equal(cb.lower(root.get("partOfSpeech")),part.strip().toLowerCase(Locale.ROOT)));
            if (!text.isEmpty()) {
                if (exact) predicates.add(cb.equal(cb.lower(root.get("word")),text));
                else {
                    var senses = query.subquery(Integer.class);
                    var meaning = senses.from(VocabularyMeaningJpaEntity.class);
                    senses.select(cb.literal(1)).where(cb.equal(meaning.get("vocabulary"),root),cb.or(
                            cb.gt(cb.locate(cb.lower(meaning.get("translationEn")),text),0),
                            cb.gt(cb.locate(cb.lower(meaning.get("definitionEn")),text),0)));
                    predicates.add(cb.or(cb.gt(cb.locate(cb.lower(root.get("word")),text),0),
                            cb.gt(cb.locate(root.get("searchWord"),folded),0),cb.exists(senses)));
                }
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var rank = cb.<Integer>selectCase().when(cb.equal(cb.lower(root.get("word")),text),0)
                        .when(cb.equal(root.get("searchWord"),folded),1).otherwise(2);
                query.orderBy(cb.asc(rank),cb.asc(root.get("word")),cb.asc(root.get("partOfSpeech")),cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
