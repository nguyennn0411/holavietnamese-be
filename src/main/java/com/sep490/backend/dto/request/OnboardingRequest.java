package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingRequest {
    @NotBlank(message = "Ngôn ngữ mẹ đẻ không được để trống")
    private String nativeLanguage;

    @NotBlank(message = "Mục tiêu học không được để trống")
    private String learningGoal;

    @NotBlank(message = "Trình độ mục tiêu không được để trống")
    private String targetLevel;

    private Integer dailyLearningGoalMinutes = 15;
    private String country;
}
