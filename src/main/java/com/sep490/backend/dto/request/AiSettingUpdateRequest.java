package com.sep490.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingUpdateRequest {
    @NotBlank(message = "Active model is required")
    private String activeModel;

    private String systemPromptVersion;

    @NotNull
    @Min(1)
    private Integer maxTurnsPerSession;

    @NotNull
    @Min(64)
    private Integer maxTokensPerResponse;

    @NotNull
    @Min(1)
    private Integer rateLimitPerMinute;

    @NotNull
    @Min(1)
    private Integer dailySessionsPerUser;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal monthlyBudgetCapUsd;

    @NotNull
    @Min(1)
    private Integer costAlertThresholdPercent;

    private String enabledModes;
}
