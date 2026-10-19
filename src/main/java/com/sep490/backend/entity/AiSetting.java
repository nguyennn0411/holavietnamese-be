package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_model", nullable = false, length = 100)
    @Builder.Default
    private String activeModel = "gemini-1.5-flash";

    @Column(name = "system_prompt_version", nullable = false, length = 50)
    @Builder.Default
    private String systemPromptVersion = "v2.2.0-stable";

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "max_turns_per_session", nullable = false)
    @Builder.Default
    private Integer maxTurnsPerSession = 25;

    @Column(name = "max_tokens_per_response", nullable = false)
    @Builder.Default
    private Integer maxTokensPerResponse = 1024;

    @Column(name = "rate_limit_per_minute", nullable = false)
    @Builder.Default
    private Integer rateLimitPerMinute = 30;

    @Column(name = "daily_sessions_per_user", nullable = false)
    @Builder.Default
    private Integer dailySessionsPerUser = 40;

    @Column(name = "monthly_budget_cap_usd", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monthlyBudgetCapUsd = new BigDecimal("150.00");

    @Column(name = "current_month_spend_usd", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal currentMonthSpendUsd = new BigDecimal("42.68");

    @Column(name = "cost_alert_threshold_percent", nullable = false)
    @Builder.Default
    private Integer costAlertThresholdPercent = 80;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "api_key_configured", nullable = false)
    @Builder.Default
    private Boolean apiKeyConfigured = false;

    @Column(name = "enabled_modes", nullable = false, length = 500)
    @Builder.Default
    private String enabledModes = "tutor,grammar,translation,conversation,culture_guide,travel,regional";

    @Column(name = "context_options", nullable = false, length = 500)
    @Builder.Default
    private String contextOptions = "learner_level,course,destination,vocabulary";

    @Column(name = "moderation_enabled", nullable = false)
    @Builder.Default
    private Boolean moderationEnabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
