package com.sep490.backend.modules.ai.entity;
import com.sep490.backend.modules.auth.entity.User;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLog {

    @Id
    @Column(name = "id", length = 100, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "mode", length = 50, nullable = false)
    private String mode;

    @Column(name = "tokens_in", nullable = false)
    @Builder.Default
    private Integer tokensIn = 0;

    @Column(name = "tokens_out", nullable = false)
    @Builder.Default
    private Integer tokensOut = 0;

    @Column(name = "latency_ms", nullable = false)
    @Builder.Default
    private Integer latencyMs = 0;

    @Column(name = "cost_usd", precision = 10, scale = 5, nullable = false)
    @Builder.Default
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "status_code", nullable = false)
    @Builder.Default
    private Integer statusCode = 200;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
