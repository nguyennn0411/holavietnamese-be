package com.sep490.backend.modules.learning.dto.response;

import com.sep490.backend.modules.learning.entity.enums.EnrollmentStatus;
public record MyCourseResponse(Long enrollmentId, Long courseId, String title, String thumbnailUrl, String level,
                               int totalLessons, int completedLessons, int progressPercentage,
                               EnrollmentStatus status, Long lastAccessedLessonId) {}
