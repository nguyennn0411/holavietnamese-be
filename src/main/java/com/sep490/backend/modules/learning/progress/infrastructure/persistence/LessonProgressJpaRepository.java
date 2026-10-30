package com.sep490.backend.modules.learning.progress.infrastructure.persistence;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.LessonProgressJpaEntity;


import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressJpaEntity, Long> {
    Optional<LessonProgressJpaEntity> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);
    List<LessonProgressJpaEntity> findByEnrollmentId(Long enrollmentId);
}
