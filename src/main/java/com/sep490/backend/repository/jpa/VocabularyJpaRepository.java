package com.sep490.backend.repository.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import com.sep490.backend.entity.VocabularyJpaEntity;
public interface VocabularyJpaRepository extends JpaRepository<VocabularyJpaEntity, Long> {
    Optional<VocabularyJpaEntity> findByIdAndUserId(Long id, Long userId);
    @Query("""
        select v from VocabularyJpaEntity v left join v.lesson l
        where v.user.id = :userId
        and (:courseId is null or l.course.id = :courseId)
        and (:lessonId is null or l.id = :lessonId)
        and (:search = '' or locate(lower(:search), lower(v.word)) > 0 or locate(lower(:search), lower(v.meaning)) > 0)
        order by v.createdAt desc, v.id desc
        """)
    List<VocabularyJpaEntity> search(Long userId, String search, Long courseId, Long lessonId);
}
