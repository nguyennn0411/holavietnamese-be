package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.VocabularyWordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VocabularyWordJpaRepository
        extends JpaRepository<VocabularyWordJpaEntity, Long>,
        JpaSpecificationExecutor<VocabularyWordJpaEntity> {
}