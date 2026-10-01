package com.sep490.backend.dto.model;

import java.time.Instant;
import com.sep490.backend.entity.enums.LessonStatus;
public record LessonProgress(Long id, Long enrollmentId, Long lessonId, LessonStatus status, Instant startedAt, Instant completedAt) {
    public static LessonProgress start(Long enrollmentId, Long lessonId, Instant now) {
        return new LessonProgress(null, enrollmentId, lessonId, LessonStatus.IN_PROGRESS, now, null);
    }
    public LessonProgress complete(Instant now) {
        return status == LessonStatus.COMPLETED ? this : new LessonProgress(id, enrollmentId, lessonId, LessonStatus.COMPLETED, startedAt == null ? now : startedAt, now);
    }
}
