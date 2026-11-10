package com.sep490.backend.modules.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AdminUserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String nativeLanguage;
    private String learningGoal;
    private String targetLevel;
    private String country;
    private String status;
    private Boolean emailVerified;
    private Boolean onboardingCompleted;
    private Integer streakCount;
    private Integer totalXp;
    private String role;
    private String createdAt;
}
