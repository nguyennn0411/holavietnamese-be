package com.sep490.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class XpRuleRequest {
    @NotBlank(message = "Event type is required")
    @Size(max = 50, message = "Event type must be at most 50 characters")
    private String eventType;

    @NotBlank(message = "Event name is required")
    @Size(max = 100, message = "Event name must be at most 100 characters")
    private String eventName;

    @NotNull(message = "XP reward is required")
    @Min(value = 0, message = "XP reward must be non-negative")
    private Integer xpReward;

    @Min(value = 0, message = "Daily cap must be non-negative")
    private Integer dailyCap;

    private Boolean active;
}
