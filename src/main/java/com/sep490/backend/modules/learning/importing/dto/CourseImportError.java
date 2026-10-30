package com.sep490.backend.modules.learning.importing.dto;

public record CourseImportError(String sheet, int row, String column, String value, String message) {}
