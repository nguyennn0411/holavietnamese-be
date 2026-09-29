package com.sep490.backend.controller;


import com.sep490.backend.dto.courseimport.*;
import com.sep490.backend.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/admin/courses/import")
public class CourseImportController {
    private final CourseImportService service;
    public CourseImportController(CourseImportService service) {
        this.service = service;
    }
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportPreview validate(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "CREATE_ONLY") ImportMode importMode) throws IOException {
        return service.validate(file.getBytes(), file.getOriginalFilename(), importMode);
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportPreview importCourse(@RequestParam("file") MultipartFile file,
            @RequestParam ImportMode importMode) throws IOException {
        return service.importCourse(file.getBytes(), file.getOriginalFilename(), importMode);
    }
}
