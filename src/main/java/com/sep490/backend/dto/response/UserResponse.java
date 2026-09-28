package com.sep490.backend.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    Integer id;
    String username;
    String email;
    String fullName;
    String phoneNumber;
    String avatarUrl;
    String nativeLanguage;
    String learningGoal;
    String targetLevel;
    String status;
    Set<String> roles;
    LocalDateTime createdAt;
}
