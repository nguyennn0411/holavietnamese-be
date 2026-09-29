package com.sep490.backend.dto.courseimport;

import java.util.*;
public record CourseImportPreview(String courseCode, ImportMode mode, boolean success,
                                  Map<String, Integer> statistics,
                                  List<CourseImportError> warnings, List<CourseImportError> errors) {
    public CourseImportPreview { statistics = Map.copyOf(statistics); warnings = List.copyOf(warnings); errors = List.copyOf(errors); }
}
