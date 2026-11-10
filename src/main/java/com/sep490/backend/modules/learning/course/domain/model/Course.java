package com.sep490.backend.modules.learning.course.domain.model;
import com.sep490.backend.modules.learning.course.domain.CourseStatus;


public record Course(Long id, String title, String description, String thumbnailUrl, String level,
                     int estimatedDuration, CourseStatus status) {}
