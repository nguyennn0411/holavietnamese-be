package com.sep490.backend.dto.response;

import java.time.Instant;
import com.sep490.backend.entity.enums.LessonStatus;
public record LessonResponse(Long id, Long courseId, String title, String description, int lessonOrder,
    String content, String videoUrl, String audioUrl, int estimatedDuration, LessonStatus learningStatus,
    Long previousLessonId, Long nextLessonId, Instant startedAt, Instant completedAt) {}
