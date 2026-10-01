package com.sep490.backend.exception;

import com.sep490.backend.dto.courseimport.CourseImportPreview;
import com.sep490.backend.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- Course Import Exception Handlers ---

    @ExceptionHandler(CourseImportRejected.class)
    public ResponseEntity<CourseImportPreview> handleImportRejected(CourseImportRejected e) {
        return ResponseEntity.badRequest().body(e.preview());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(Exception e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, "Upload limit is 10 MiB per file."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Required file or importMode is missing."));
    }

    @ExceptionHandler(LearningException.class)
    public ResponseEntity<ApiResponse<Void>> handleLearningException(LearningException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "This record already exists or conflicts with related data. Refresh and try again."));
    }

    // --- Auth & General Exception Handlers ---

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Dữ liệu yêu cầu không đúng định dạng"));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            org.springframework.security.core.AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED.getCode(),
                        ErrorCode.UNAUTHENTICATED.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(),
                        ErrorCode.UNAUTHORIZED.getMessage()));
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Dữ liệu không hợp lệ";
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code(9998)
                .message(message)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(
            ResponseStatusException exception) {
        String message = exception.getReason() != null
                ? exception.getReason()
                : exception.getMessage();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(exception.getStatusCode().value(), message));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleClientDisconnected(AsyncRequestNotUsableException exception) {
        log.debug("Client đã ngắt request bất đồng bộ: {}", exception.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(
            Exception exception, HttpServletResponse response) {
        if (isStreamingOrCommitted(response)) {
            if (isClientAbort(exception)) {
                log.debug("Client ngắt streaming: {}", exception.getMessage());
            } else {
                log.error("Lỗi trên response đã commit, không thể trả body", exception);
            }
            return null;
        }

        log.error("Unhandled exception", exception);
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String detail = root.getMessage();
        String message = (detail != null && !detail.isBlank())
                ? ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage() + ": " + detail
                : ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage();

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(message)
                .build();
        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(body);
    }

    private boolean isStreamingOrCommitted(HttpServletResponse response) {
        if (response == null) return false;
        String contentType = response.getContentType();
        return response.isCommitted()
                || (contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private boolean isClientAbort(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof IOException) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }
}
