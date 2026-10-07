package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.VocabularyExampleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabularyExampleJpaRepository
        extends JpaRepository<VocabularyExampleJpaEntity, Long> {
}