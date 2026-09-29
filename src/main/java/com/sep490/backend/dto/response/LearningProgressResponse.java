package com.sep490.backend.dto.response;

import java.time.Instant;
import java.util.List;
import com.sep490.backend.entity.enums.*;
public record LearningProgressResponse(Long courseId, int totalLessons, int completedLessons, int progressPercentage,
    Long lastAccessedLessonId, Instant lastAccessedAt, EnrollmentStatus status, List<LessonState> lessons) {
    public record LessonState(Long lessonId, LessonStatus status, Instant startedAt, Instant completedAt) {}
}
