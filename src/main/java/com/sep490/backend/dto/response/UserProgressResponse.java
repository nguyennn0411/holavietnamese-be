package com.sep490.backend.dto.response;

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
    private Integer dailyGoalMinutes;
    private LocalDate lastActivityDate;
    private Integer completedLessonsCount;
    private Integer learnedVocabulariesCount;
    private List<XpHistoryItem> recentXpTransactions;

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
