package com.sep490.backend.repository.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import com.sep490.backend.entity.CourseJpaEntity;
import com.sep490.backend.entity.enums.CourseStatus;
public interface CourseJpaRepository extends JpaRepository<CourseJpaEntity, Long> {
    List<CourseJpaEntity> findByStatusOrderByIdAsc(CourseStatus status);
    @Query("select count(l) from LessonJpaEntity l where l.course.id = :courseId and l.published = true")
    int countPublishedLessons(Long courseId);
}
