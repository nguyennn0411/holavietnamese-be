package com.sep490.backend.mapper;

import com.sep490.backend.entity.*;
import com.sep490.backend.dto.model.*;
public final class LessonMapper {
    private LessonMapper() {}
    public static Lesson toDomain(LessonJpaEntity e) { return new Lesson(e.getId(), e.getCourse().getId(), e.getTitle(), e.getDescription(), e.getLessonOrder(), e.getContent(), e.getVideoUrl(), e.getAudioUrl(), e.getEstimatedDuration(), e.isPublished()); }
    public static LessonProgress toDomain(LessonProgressJpaEntity e) { return new LessonProgress(e.getId(), e.getEnrollment().getId(), e.getLesson().getId(), e.getStatus(), e.getStartedAt(), e.getCompletedAt()); }
}
