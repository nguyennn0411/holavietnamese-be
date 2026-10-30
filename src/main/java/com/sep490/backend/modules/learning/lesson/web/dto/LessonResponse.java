package com.sep490.backend.modules.learning.lesson.web.dto;
import com.sep490.backend.modules.learning.lesson.domain.LessonStatus;



import java.time.Instant;
public record LessonResponse(Long id, Long courseId, String title, String description, int lessonOrder,
    String content, String videoUrl, String audioUrl, int estimatedDuration, LessonStatus learningStatus,
    Long previousLessonId, Long nextLessonId, Instant startedAt, Instant completedAt) {}
