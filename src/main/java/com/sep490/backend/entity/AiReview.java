package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiReview {

    @Id
    @Column(length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiSession session;

    public String getSessionId() {
        return session != null ? session.getId() : null;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "scenario_id", length = 100)
    private String scenarioId;

    @Column(name = "scenario_title")
    private String scenarioTitle;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "user_feedback", columnDefinition = "TEXT")
    private String userFeedback;

    @Column(name = "tagged_issues_json", columnDefinition = "JSON")
    private String taggedIssuesJson;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "reviewed_by_admin", nullable = false)
    @Builder.Default
    private Boolean reviewedByAdmin = false;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "APPROVED";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
