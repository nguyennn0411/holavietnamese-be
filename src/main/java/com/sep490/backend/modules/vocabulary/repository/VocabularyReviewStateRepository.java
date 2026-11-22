package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewState;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Luu va doc lich on tap hien tai cua tung muc so tu. */
public interface VocabularyReviewStateRepository
        extends JpaRepository<VocabularyReviewState, Long> {

    @Query(
            """
            select min(state.nextReviewAt)
            from VocabularyReviewState state
            where state.vocabularyEntry.user.id = :userId
              and state.nextReviewAt > :now
            """)
    Optional<Instant> findNextDueAt(
            @Param("userId") Long userId, @Param("now") Instant now);
}
