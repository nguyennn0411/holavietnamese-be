package com.sep490.backend.modules.learning.importing.exception;

import com.sep490.backend.modules.learning.importing.dto.CourseImportPreview;
public class CourseImportRejected extends RuntimeException {
    private final CourseImportPreview preview;
    public CourseImportRejected(CourseImportPreview preview) { super("Course import validation failed."); this.preview = preview; }
    public CourseImportPreview preview() { return preview; }
}
