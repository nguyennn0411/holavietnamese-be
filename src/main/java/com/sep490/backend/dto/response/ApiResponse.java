package com.sep490.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @Builder.Default
    private int code = 1000;
    private String message;
    private T result;
    private String error;

    public static final int SUCCESS_CODE = 1000;
    public static final int DEFAULT_ERROR_CODE = 9999;

    public static <T> ApiResponse<T> success(String message, T result) {
        return ApiResponse.<T>builder()
                .code(SUCCESS_CODE)
                .message(message)
                .result(result)
                .build();
    }

    public static <T> ApiResponse<T> success(T result) {
        return success(null, result);
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .code(SUCCESS_CODE)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message, String detail) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .error(detail)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(DEFAULT_ERROR_CODE, message);
    }

    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
