package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class BadgeResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String category;
    private String conditionType;
    private Integer conditionValue;
    private Integer xpReward;
    private Boolean isUnlocked;
    private LocalDateTime unlockedAt;
    private Integer badgeLevel;
}
