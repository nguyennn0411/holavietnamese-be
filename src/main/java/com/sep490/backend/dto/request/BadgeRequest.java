package com.sep490.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BadgeRequest {
    @NotBlank(message = "Badge code is required")
    @Size(max = 50, message = "Badge code must be at most 50 characters")
    private String code;

    @NotBlank(message = "Badge name is required")
    @Size(max = 100, message = "Badge name must be at most 100 characters")
    private String name;

    private String description;

    @Size(max = 500, message = "Icon URL must be at most 500 characters")
    private String iconUrl;

    @Size(max = 50, message = "Category must be at most 50 characters")
    private String category;

    @NotBlank(message = "Condition type is required")
    @Size(max = 50, message = "Condition type must be at most 50 characters")
    private String conditionType;

    @NotNull(message = "Condition value is required")
    @Min(value = 1, message = "Condition value must be at least 1")
    private Integer conditionValue;

    @NotNull(message = "XP reward is required")
    @Min(value = 0, message = "XP reward must be non-negative")
    private Integer xpReward;

    @Size(max = 20, message = "Status must be at most 20 characters")
    private String status;
}
