package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
public class AdminUserResponse {
    private Integer id;
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
    private Set<String> roles;
    private String createdAt;
}
