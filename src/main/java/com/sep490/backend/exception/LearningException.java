package com.sep490.backend.exception;

public class LearningException extends RuntimeException {
    public enum Kind { NOT_FOUND, CONFLICT, FORBIDDEN, INVALID }
    private final Kind kind;
    public LearningException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
    public static LearningException notFound(String item) { return new LearningException(Kind.NOT_FOUND, item + " was not found."); }
}
