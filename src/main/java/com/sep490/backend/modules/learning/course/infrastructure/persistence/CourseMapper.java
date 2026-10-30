package com.sep490.backend.modules.learning.course.infrastructure.persistence;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.entity.CourseJpaEntity;


public final class CourseMapper {
    private CourseMapper() {}
    public static Course toDomain(CourseJpaEntity e) { return new Course(e.getId(), e.getTitle(), e.getDescription(), e.getThumbnailUrl(), e.getLevel(), e.getEstimatedDuration(), e.getStatus()); }
}
