package com.sep490.backend.repository.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sep490.backend.entity.LessonJpaEntity;
public interface LessonJpaRepository extends JpaRepository<LessonJpaEntity, Long> {
    List<LessonJpaEntity> findByCourseIdAndPublishedTrueOrderByLessonOrderAscIdAsc(Long courseId);
}
