package com.sep490.backend.repository;

import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.entity.VocabularyTopicJpaEntity;
import com.sep490.backend.mapper.VocabularyTopicMapper;
import com.sep490.backend.repository.jpa.VocabularyTopicJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VocabularyTopicRepository {

    private final VocabularyTopicJpaRepository repository;

    public List<VocabularyTopic> findAll() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc()
                .stream()
                .map(VocabularyTopicMapper::toDomain)
                .toList();
    }

    public Optional<VocabularyTopic> findById(Long id) {
        return repository.findById(id)
                .map(VocabularyTopicMapper::toDomain);
    }

    public boolean existsBySlug(String slug) {
        return repository.existsBySlugIgnoreCase(slug);
    }

    public boolean existsBySlugExceptId(String slug, Long id) {
        return repository.existsBySlugIgnoreCaseAndIdNot(slug, id);
    }

    public VocabularyTopic save(VocabularyTopic topic) {
        VocabularyTopicJpaEntity entity;

        if (topic.id() == null) {
            entity = new VocabularyTopicJpaEntity();
        } else {
            entity = repository.findById(topic.id())
                    .orElseThrow();
        }

        entity.setName(topic.name());
        entity.setSlug(topic.slug());
        entity.setDescription(topic.description());
        entity.setDisplayOrder(topic.displayOrder());
        entity.setStatus(topic.status());

        return VocabularyTopicMapper.toDomain(
                repository.saveAndFlush(entity)
        );
    }

    public long count() {
        return repository.count();
    }
}