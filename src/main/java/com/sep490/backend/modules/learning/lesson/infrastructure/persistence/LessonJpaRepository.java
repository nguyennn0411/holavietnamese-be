package com.sep490.backend.modules.learning.lesson.infrastructure.persistence;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;



import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonJpaRepository extends JpaRepository<LessonJpaEntity, Long> {
    List<LessonJpaEntity> findByCourseIdAndPublishedTrueOrderByLessonOrderAscIdAsc(Long courseId);
}
