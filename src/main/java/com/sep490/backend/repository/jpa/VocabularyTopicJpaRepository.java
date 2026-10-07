package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.VocabularyTopicJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface VocabularyTopicJpaRepository
        extends JpaRepository<VocabularyTopicJpaEntity, Long>,
        JpaSpecificationExecutor<VocabularyTopicJpaEntity> {

    List<VocabularyTopicJpaEntity> findAllByOrderByDisplayOrderAscIdAsc();

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}