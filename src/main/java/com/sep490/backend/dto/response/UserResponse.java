package com.sep490.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    Integer id;
    String username;
    String email;
    String fullName;
    String phoneNumber;
    String avatarUrl;
    String country;
    String nativeLanguage;
    String learningGoal;
    String targetLevel;
    Integer dailyLearningGoalMinutes;
    Double audioSpeed;
    Boolean pronunciationHintsEnabled;
    Boolean autoTranslateEnabled;
    Boolean notificationsEnabled;
    Boolean onboardingCompleted;
    Boolean emailVerified;
    Integer streakCount;
    Integer totalXp;
    String status;
    Set<String> roles;
    String token;
    LocalDateTime createdAt;
}
