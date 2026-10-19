
package com.sep490.backend.repository.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import com.sep490.backend.entity.VocabularyJpaEntity;

public interface VocabularyJpaRepository extends JpaRepository<VocabularyJpaEntity, Long> {

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VocabularyJpaEntity v where v.id = :id and v.user.id = :userId")
    Optional<VocabularyJpaEntity> lockOwned(Long id, Long userId);

    // Bulk delete lets the database cascade review state/logs even if a managed
    // @MapsId review state still references this entry in the current transaction.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VocabularyJpaEntity v where v.id = :id and v.user.id = :userId")
    int deleteOwned(Long id, Long userId);

    @Query("""
        select v from VocabularyJpaEntity v
        left join VocabularyReviewStateJpaEntity s on s.id = v.id
        where v.user.id = :userId and (s.id is null or s.nextReviewAt <= :now)
        and not exists (select log.id from VocabularyReviewLogJpaEntity log
            where log.vocabularyEntry.id = v.id and log.sessionId = :sessionId)
        order by coalesce(s.nextReviewAt, v.createdAt), v.id
        """)
    List<VocabularyJpaEntity> findDue(Long userId, java.time.Instant now, String sessionId);

    Optional<VocabularyJpaEntity> findByIdAndUserId(Long id, Long userId);

    int countByUserId(Long userId);

    Optional<VocabularyJpaEntity> findByUser_IdAndMasterVocabulary_Id(
            Long userId,
            Long masterVocabularyId
    );

    @Query("""
        select v from VocabularyJpaEntity v left join v.lesson l
        where v.user.id = :userId
        and (:courseId is null or l.course.id = :courseId)
        and (:lessonId is null or l.id = :lessonId)
        and (:search = '' or locate(lower(:search), lower(v.word)) > 0 or locate(lower(:search), lower(v.meaning)) > 0)
        order by v.createdAt desc, v.id desc
        """)
    List<VocabularyJpaEntity> search(
            Long userId,
            String search,
            Long courseId,
            Long lessonId
    );
}
