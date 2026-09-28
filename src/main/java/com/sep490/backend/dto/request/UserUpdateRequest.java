package com.sep490.backend.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String fullName;
    String phoneNumber;
    String avatarUrl;
    String nativeLanguage;
    String learningGoal;
    String targetLevel;
}
