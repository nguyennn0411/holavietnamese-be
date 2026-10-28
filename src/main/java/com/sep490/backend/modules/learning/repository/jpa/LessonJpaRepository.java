package com.sep490.backend.modules.learning.repository.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sep490.backend.modules.learning.entity.LessonJpaEntity;
public interface LessonJpaRepository extends JpaRepository<LessonJpaEntity, Long> {
    List<LessonJpaEntity> findByCourseIdAndPublishedTrueOrderByLessonOrderAscIdAsc(Long courseId);
}
