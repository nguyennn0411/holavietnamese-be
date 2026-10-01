package com.sep490.backend.service;
import com.sep490.backend.service.importing.CourseImportValidator;
import com.sep490.backend.service.importing.ExcelCourseParser;
import com.sep490.backend.exception.CourseImportRejected;

import com.sep490.backend.dto.courseimport.*;
import com.sep490.backend.service.*;
import com.sep490.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CourseImportService {
    private final ExcelCourseParser parser;
    private final CourseImportValidator validator;
    private final CourseImportRepository repository;
    public CourseImportService(ExcelCourseParser parser, CourseImportValidator validator, CourseImportRepository repository) {
        this.parser = parser; this.validator = validator; this.repository = repository;
    }
    @Transactional(readOnly = true)
    public CourseImportPreview validate(byte[] bytes, String filename, ImportMode mode) {
        return preview(parser.parse(bytes, filename), mode);
    }
    @Transactional(rollbackFor = Exception.class)
    public CourseImportPreview importCourse(byte[] bytes, String filename, ImportMode mode) {
        var command = parser.parse(bytes, filename);
        // Serialize updates of an existing course. Unique database keys arbitrate simultaneous creates.
        if (command.courseCode() != null) repository.lockCourse(command.courseCode());
        var preview = preview(command, mode);
        if (!preview.success()) throw new CourseImportRejected(preview);
        repository.save(command);
        return preview;
    }
    private CourseImportPreview preview(CourseImportCommand command, ImportMode mode) {
        var errors = new ArrayList<>(validator.validate(command));
        if (errors.isEmpty()) errors.addAll(repository.validate(command, mode));
        return new CourseImportPreview(command.courseCode(), mode, errors.isEmpty(), command.statistics(), command.warnings(), errors);
    }
}
