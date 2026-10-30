package com.sep490.backend.modules.learning.lesson.infrastructure.persistence;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;
import com.sep490.backend.modules.learning.progress.domain.model.LessonProgress;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.LessonProgressJpaEntity;



public final class LessonMapper {
    private LessonMapper() {}
    public static Lesson toDomain(LessonJpaEntity e) { return new Lesson(e.getId(), e.getCourse().getId(), e.getTitle(), e.getDescription(), e.getLessonOrder(), e.getContent(), e.getVideoUrl(), e.getAudioUrl(), e.getEstimatedDuration(), e.isPublished()); }
    public static LessonProgress toDomain(LessonProgressJpaEntity e) { return new LessonProgress(e.getId(), e.getEnrollment().getId(), e.getLesson().getId(), e.getStatus(), e.getStartedAt(), e.getCompletedAt()); }
}
