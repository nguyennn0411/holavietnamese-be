package com.sep490.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    // === General ===
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),

    // === Auth (1xxx) ===
    UNAUTHENTICATED(1001, "Tài khoản hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1002, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    USER_NOT_EXISTED(1003, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    USER_DEACTIVATED(1004, "Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN),
    USERNAME_EXISTED(1005, "Tên đăng nhập đã được sử dụng", HttpStatus.CONFLICT),
    EMAIL_EXISTED(1006, "Email đã được sử dụng", HttpStatus.CONFLICT),
    INVALID_PASSWORD_LENGTH(1007, "Mật khẩu phải có từ 8 đến 64 ký tự", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD_FORMAT(1008, "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số", HttpStatus.BAD_REQUEST),
    ROLE_NOT_FOUND(1009, "Vai trò không tồn tại trong hệ thống", HttpStatus.BAD_REQUEST),
    INVALID_FULL_NAME(1010, "Họ và tên không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL(1011, "Email không hợp lệ", HttpStatus.BAD_REQUEST),

    // === OTP / Forgot Password (2xxx) ===
    TOO_MANY_OTP_REQUESTS(2001, "Quá nhiều yêu cầu OTP. Vui lòng thử lại sau.", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_OTP(2002, "Mã OTP không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_FOUND(2003, "Email không tìm thấy trong hệ thống", HttpStatus.NOT_FOUND),
    PASSWORD_SAME_AS_CURRENT(2004, "Mật khẩu mới không được trùng với mật khẩu hiện tại", HttpStatus.BAD_REQUEST),
    ;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }

    private final int code;
    private final String message;
    private final HttpStatusCode statusCode;
}
