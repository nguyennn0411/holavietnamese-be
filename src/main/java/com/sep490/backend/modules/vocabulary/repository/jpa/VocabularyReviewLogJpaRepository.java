package com.sep490.backend.modules.vocabulary.repository.jpa;

import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLogJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface VocabularyReviewLogJpaRepository extends JpaRepository<VocabularyReviewLogJpaEntity, Long> {
    List<VocabularyReviewLogJpaEntity> findByVocabularyEntry_User_IdAndSessionIdOrderByReviewedAtAscIdAsc(Long userId, String sessionId);
    Optional<VocabularyReviewLogJpaEntity> findByVocabularyEntry_IdAndSessionId(Long entryId, String sessionId);
}
