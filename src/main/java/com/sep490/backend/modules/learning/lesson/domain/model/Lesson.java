package com.sep490.backend.modules.learning.lesson.domain.model;

public record Lesson(Long id, Long courseId, String title, String description, int lessonOrder,
                     String content, String videoUrl, String audioUrl, int estimatedDuration, boolean published) {}
