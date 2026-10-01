package com.sep490.backend.exception;

import com.sep490.backend.dto.courseimport.CourseImportPreview;
public class CourseImportRejected extends RuntimeException {
    private final CourseImportPreview preview;
    public CourseImportRejected(CourseImportPreview preview) { super("Course import validation failed."); this.preview = preview; }
    public CourseImportPreview preview() { return preview; }
}
