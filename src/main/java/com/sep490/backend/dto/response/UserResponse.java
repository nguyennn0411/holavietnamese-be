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
    Long id;
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
    String token;
    LocalDateTime createdAt;
}
