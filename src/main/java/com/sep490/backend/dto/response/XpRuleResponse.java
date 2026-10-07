package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class XpRuleResponse {
    private Long id;
    private String eventType;
    private String eventName;
    private Integer xpReward;
    private Integer dailyCap;
    private Boolean active;
    private LocalDateTime createdAt;
}
