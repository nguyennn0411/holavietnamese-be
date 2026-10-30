package com.sep490.backend.modules.learning.progress.web.dto;
import com.sep490.backend.modules.learning.lesson.domain.LessonStatus;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;



import java.time.Instant;
import java.util.List;
public record LearningProgressResponse(Long courseId, int totalLessons, int completedLessons, int progressPercentage,
    Long lastAccessedLessonId, Instant lastAccessedAt, EnrollmentStatus status, List<LessonState> lessons) {
    public record LessonState(Long lessonId, LessonStatus status, Instant startedAt, Instant completedAt) {}
}
