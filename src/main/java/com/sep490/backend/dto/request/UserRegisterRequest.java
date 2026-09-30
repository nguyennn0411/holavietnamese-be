package com.sep490.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRegisterRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải từ 3 đến 50 ký tự")
    String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 64, message = "Mật khẩu phải từ 6 đến 64 ký tự")
    String password;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    String email;

    String fullName;

    String phoneNumber;

    /**
     * Ngôn ngữ mẹ đẻ (e.g., "en", "ko", "ja", "zh", "fr")
     */
    String nativeLanguage;

    /**
     * Mục tiêu học tiếng Việt (e.g., "travel", "work", "exam_vsl")
     */
    String learningGoal;

    /**
     * Trình độ mục tiêu (e.g., "A1", "A2", "B1")
     */
    String targetLevel;
}
