package com.sep490.backend.modules.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_scenarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiScenario {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String level = "A1";

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String topic = "dining";

    @Column(length = 100, nullable = false)
    @Builder.Default
    private String destination = "Hà Nội";

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String difficulty = "Dễ";

    @Column(length = 50)
    @Builder.Default
    private String duration = "5-10 phút";

    @Column(length = 1000)
    private String thumbnail;

    @Column(name = "cover_image", length = 1000)
    private String coverImage;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(name = "ai_role_name", nullable = false, length = 100)
    private String aiRoleName;

    @Column(name = "ai_role_title")
    private String aiRoleTitle;

    @Column(name = "ai_role_avatar", length = 50)
    private String aiRoleAvatar;

    @Column(name = "ai_role_tone", columnDefinition = "TEXT")
    private String aiRoleTone;

    @Column(name = "ai_role_initial_greeting", columnDefinition = "TEXT", nullable = false)
    private String aiRoleInitialGreeting;

    @Column(name = "learner_role_title")
    private String learnerRoleTitle;

    @Column(name = "learner_role_context", columnDefinition = "TEXT")
    private String learnerRoleContext;

    @Column(name = "objectives_json", columnDefinition = "JSON")
    private String objectivesJson;

    @Column(name = "vocabulary_hints_json", columnDefinition = "JSON")
    private String vocabularyHintsJson;

    @Column(name = "cultural_tip", columnDefinition = "TEXT")
    private String culturalTip;

    @Column(name = "sample_hints_json", columnDefinition = "JSON")
    private String sampleHintsJson;

    @Column(name = "max_turns")
    @Builder.Default
    private Integer maxTurns = 10;

    @Column(name = "criteria_json", columnDefinition = "JSON")
    private String criteriaJson;

    @Column(name = "completions_count", nullable = false)
    @Builder.Default
    private Integer completionsCount = 0;

    @Column(name = "avg_score", nullable = false)
    @Builder.Default
    private Integer avgScore = 0;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
