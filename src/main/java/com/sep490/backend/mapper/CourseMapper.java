package com.sep490.backend.mapper;

import com.sep490.backend.entity.CourseJpaEntity;
import com.sep490.backend.dto.model.Course;
public final class CourseMapper {
    private CourseMapper() {}
    public static Course toDomain(CourseJpaEntity e) { return new Course(e.getId(), e.getTitle(), e.getDescription(), e.getThumbnailUrl(), e.getLevel(), e.getEstimatedDuration(), e.getStatus()); }
}
