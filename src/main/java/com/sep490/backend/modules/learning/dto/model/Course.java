package com.sep490.backend.modules.learning.dto.model;

import com.sep490.backend.modules.learning.entity.enums.CourseStatus;
public record Course(Long id, String title, String description, String thumbnailUrl, String level,
                     int estimatedDuration, CourseStatus status) {}
