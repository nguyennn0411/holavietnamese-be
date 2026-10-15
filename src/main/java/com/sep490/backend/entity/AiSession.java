package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSession {

    @Id
    @Column(length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String mode;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private AiScenario scenario;

    public String getScenarioId() {
        return scenario != null ? scenario.getId() : null;
    }

    @Column(name = "learner_level", length = 20)
    private String learnerLevel;

    @Column(length = 100)
    private String destination;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "turns_count")
    @Builder.Default
    private Integer turnsCount = 0;

    @Column(name = "total_score")
    private Integer totalScore;

    public Integer getScore() {
        return totalScore;
    }

    public void setScore(Integer score) {
        this.totalScore = score;
    }

    @Column(length = 50)
    private String rating;

    @Column(name = "feedback_summary", columnDefinition = "TEXT")
    private String feedbackSummary;

    @Column(name = "stats_json", columnDefinition = "JSON")
    private String statsJson;

    @Column(name = "completed_tasks_json", columnDefinition = "JSON")
    private String completedTasksJson;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "corrections_json", columnDefinition = "JSON")
    private String correctionsJson;

    @Column(name = "practiced_expressions_json", columnDefinition = "JSON")
    private String practicedExpressionsJson;

    @Column(name = "started_at")
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
