package com.sep490.backend.modules.learning.dto.response;

import com.sep490.backend.modules.learning.dto.model.Course;
import com.sep490.backend.modules.learning.entity.enums.CourseStatus;
public record CourseResponse(Long id, String title, String description, String thumbnailUrl, String level,
                             int estimatedDuration, int totalLessons, CourseStatus status) {
    public static CourseResponse from(Course c, int count) {
        return new CourseResponse(c.id(), c.title(), c.description(), c.thumbnailUrl(), c.level(), c.estimatedDuration(), count, c.status());
    }
}
