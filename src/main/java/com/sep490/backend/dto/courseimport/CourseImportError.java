package com.sep490.backend.dto.courseimport;

public record CourseImportError(String sheet, int row, String column, String value, String message) {}
