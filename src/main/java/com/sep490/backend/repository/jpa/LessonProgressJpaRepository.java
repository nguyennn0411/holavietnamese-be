package com.sep490.backend.repository.jpa;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sep490.backend.entity.LessonProgressJpaEntity;
public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressJpaEntity, Long> {
    Optional<LessonProgressJpaEntity> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);
    List<LessonProgressJpaEntity> findByEnrollmentId(Long enrollmentId);
}
