package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface VocabularyReviewLogRepository extends JpaRepository<VocabularyReviewLog, Long> {
    /** Lay ket qua da danh gia trong phien theo thu tu thoi gian va ID. */
    List<VocabularyReviewLog> findByVocabularyEntry_User_IdAndSessionIdOrderByReviewedAtAscIdAsc(
            Long userId, String sessionId);

    /** Tim ket qua cu de request gui lai khong tao log hoac doi lich lan nua. */
    Optional<VocabularyReviewLog> findByVocabularyEntry_IdAndSessionId(
            Long entryId, String sessionId);
}
