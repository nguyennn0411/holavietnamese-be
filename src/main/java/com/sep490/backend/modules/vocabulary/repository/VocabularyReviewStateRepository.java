package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewState;

import org.springframework.data.jpa.repository.JpaRepository;

/** Luu va doc lich on tap hien tai cua tung muc so tu. */
public interface VocabularyReviewStateRepository
        extends JpaRepository<VocabularyReviewState, Long> {}
