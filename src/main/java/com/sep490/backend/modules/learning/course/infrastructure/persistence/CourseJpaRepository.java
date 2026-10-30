package com.sep490.backend.modules.learning.course.infrastructure.persistence;
import com.sep490.backend.modules.learning.course.domain.CourseStatus;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.entity.CourseJpaEntity;



import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface CourseJpaRepository extends JpaRepository<CourseJpaEntity, Long> {
    List<CourseJpaEntity> findByStatusOrderByIdAsc(CourseStatus status);
    @Query("select count(l) from LessonJpaEntity l where l.course.id = :courseId and l.published = true")
    int countPublishedLessons(Long courseId);
}
