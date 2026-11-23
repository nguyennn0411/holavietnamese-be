package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Xác thực chính sách mật khẩu theo chuẩn hiện đại NIST 800-63B và các quy tắc phụ trợ.
 */
@Component
public class PasswordPolicyValidator {

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "password1234", "12345678", "123456789", "1234567890",
            "qwertyuiop", "qwerty123", "admin123", "admin1234", "holavietnamese",
            "vietnamese", "iloveyou", "welcome123", "letmein123", "passphrase",
            "11111111", "00000000", "testpassword", "testing123"
    );

    public void validate(String password, String username, String email) {
        if (password == null || password.trim().length() < 8 || password.length() > 128) {
            throw new AppException(ErrorCode.INVALID_PASSWORD_LENGTH);
        }

        String cleanPassword = password.trim().toLowerCase();

        // 1. Chặn các mật khẩu quá phổ biến / dễ đoán (NIST 800-63B)
        if (COMMON_PASSWORDS.contains(cleanPassword)) {
            throw new AppException(ErrorCode.PASSWORD_TOO_COMMON);
        }

        // 2. Không được chứa tên đăng nhập (nếu username >= 3 ký tự)
        if (username != null && username.trim().length() >= 3) {
            String cleanUsername = username.trim().toLowerCase();
            if (cleanPassword.contains(cleanUsername)) {
                throw new AppException(ErrorCode.PASSWORD_CONTAINS_USER_INFO);
            }
        }

        // 3. Không được chứa phần tiền tố email (trước @ nếu >= 3 ký tự)
        if (email != null && email.contains("@")) {
            String emailPrefix = email.substring(0, email.indexOf('@')).trim().toLowerCase();
            if (emailPrefix.length() >= 3 && cleanPassword.contains(emailPrefix)) {
                throw new AppException(ErrorCode.PASSWORD_CONTAINS_USER_INFO);
            }
        }
    }
}
