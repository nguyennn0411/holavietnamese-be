package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.VocabularyMeaningJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabularyMeaningJpaRepository
        extends JpaRepository<VocabularyMeaningJpaEntity, Long> {
}