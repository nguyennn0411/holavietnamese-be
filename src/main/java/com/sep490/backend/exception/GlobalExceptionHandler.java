package com.sep490.backend.exception;
import com.sep490.backend.exception.CourseImportRejected;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import com.sep490.backend.exception.LearningException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.sep490.backend.exception.CourseImportRejected.class)
    ResponseEntity<com.sep490.backend.dto.courseimport.CourseImportPreview> importRejected(com.sep490.backend.exception.CourseImportRejected e) {
        return ResponseEntity.badRequest().body(e.preview());
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(Exception e) { return error(HttpStatus.PAYLOAD_TOO_LARGE, "Upload limit is 10 MiB per file."); }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
                       org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> missingPart(Exception e) { return error(HttpStatus.BAD_REQUEST, "Required file or importMode is missing."); }
    public record ApiError(Instant timestamp, int status, String error, String message) {}
    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
    @ExceptionHandler(LearningException.class)
    ResponseEntity<ApiError> domain(LearningException e) {
        HttpStatus status = switch(e.kind()) { case NOT_FOUND -> HttpStatus.NOT_FOUND; case CONFLICT -> HttpStatus.CONFLICT; case FORBIDDEN -> HttpStatus.FORBIDDEN; case INVALID -> HttpStatus.BAD_REQUEST; };
        return error(status, e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, e.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid(Exception e) { return error(HttpStatus.BAD_REQUEST, "Invalid request. Check identifiers and field values."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) { return error(HttpStatus.CONFLICT, "This record already exists or conflicts with related data. Refresh and try again."); }
}
