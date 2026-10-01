package com.sep490.backend.dto.model;

public record LearningProgress(int totalLessons, int completedLessons) {
    public int percentage() { return totalLessons == 0 ? 0 : (int) Math.round(completedLessons * 100.0 / totalLessons); }
    public boolean isComplete() { return totalLessons > 0 && completedLessons == totalLessons; }
}
