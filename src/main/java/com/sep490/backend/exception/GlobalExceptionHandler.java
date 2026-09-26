package com.sep490.backend.exception;

import com.sep490.backend.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(RuntimeException exception) {
        ApiResponse<Void> apiResponse = new ApiResponse<>();

        if (exception.getMessage().equals(ErrorCode.UNAUTHENTICATED.getMessage())) {
            apiResponse.setCode(ErrorCode.UNAUTHENTICATED.getCode());
            apiResponse.setMessage(ErrorCode.UNAUTHENTICATED.getMessage());
            return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getStatusCode()).body(apiResponse);
        }

        apiResponse.setCode(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());

        return ResponseEntity.badRequest().body(apiResponse);
    }
}
