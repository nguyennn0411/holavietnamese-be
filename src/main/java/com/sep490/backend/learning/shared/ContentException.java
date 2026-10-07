package com.sep490.backend.learning.shared;

import org.springframework.http.HttpStatus;

public class ContentException extends RuntimeException {
  public final String code;
  public final HttpStatus status;

  public ContentException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static ContentException invalid(String message) {
    return new ContentException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
  }

  public static ContentException missing(String code) {
    return new ContentException(code, "The requested content was not found.", HttpStatus.NOT_FOUND);
  }

  public static ContentException conflict(String code, String message) {
    return new ContentException(code, message, HttpStatus.CONFLICT);
  }

  public static ContentException forbidden(String code, String message) {
    return new ContentException(code, message, HttpStatus.FORBIDDEN);
  }
}
