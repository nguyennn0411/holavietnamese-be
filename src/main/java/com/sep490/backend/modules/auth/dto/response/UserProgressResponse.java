package com.sep490.backend.modules.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class UserProgressResponse {
    private Integer streakCount;
    private Integer totalXp;
    private Integer hearts;
    private Boolean hasCheckedInToday;
    private Integer dailyGoalMinutes;
    private LocalDate lastActivityDate;
    private Integer completedLessonsCount;
    private Integer learnedVocabulariesCount;
    private List<XpHistoryItem> recentXpTransactions;

    // Gamification Package 1: Level system
    private Integer currentLevel;
    private String levelTitle;
    private String levelTitleEn;
    private Integer currentLevelMinXp;
    private Integer nextLevelRequiredXp;
    private Integer levelProgressPercent;

    // Gamification Package 2: Streak Freeze & Daily Goal
    private Integer streakFreezeCount;
    private LocalDate streakFreezeUsedDate;
    private Integer dailyGoalXp;
    private Integer xpEarnedToday;
    private Integer dailyGoalProgressPercent;
    private Boolean isDailyGoalCompleted;

    @Getter
    @Setter
    @Builder
    public static class XpHistoryItem {
        private Long id;
        private Integer amount;
        private String eventType;
        private String description;
        private String createdAt;
    }
}
