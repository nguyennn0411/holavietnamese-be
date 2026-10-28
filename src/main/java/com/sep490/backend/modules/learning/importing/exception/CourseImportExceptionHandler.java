package com.sep490.backend.modules.learning.importing.exception;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.learning.importing.dto.CourseImportPreview;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CourseImportExceptionHandler {

    @ExceptionHandler(CourseImportRejected.class)
    public ResponseEntity<CourseImportPreview> handleImportRejected(CourseImportRejected exception) {
        return ResponseEntity.badRequest().body(exception.preview());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, "Upload limit is 10 MiB per file."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Required file or importMode is missing."));
    }
}
