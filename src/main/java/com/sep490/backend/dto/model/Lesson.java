package com.sep490.backend.dto.model;

public record Lesson(Long id, Long courseId, String title, String description, int lessonOrder,
                     String content, String videoUrl, String audioUrl, int estimatedDuration, boolean published) {}
