package com.sep490.backend.modules.learning.course.web.dto;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;



public record MyCourseResponse(Long enrollmentId, Long courseId, String title, String thumbnailUrl, String level,
                               int totalLessons, int completedLessons, int progressPercentage,
                               EnrollmentStatus status, Long lastAccessedLessonId) {}
