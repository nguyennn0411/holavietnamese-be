package com.sep490.backend.modules.learning.importing.controller;

import com.sep490.backend.modules.learning.importing.service.CourseWorkbookService;

import com.sep490.backend.common.security.LearnerPrincipal;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/courses/workbook")
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_MANAGER')")
public class CourseWorkbookController {
  private final CourseWorkbookService workbooks;
  private static final MediaType XLSX=MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  @GetMapping("/template")
  public ResponseEntity<byte[]> template() {return file("course-studio-template.xlsx",workbooks.template());}

  @PostMapping(value="/validate",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('CONTENT_MANAGER')")
  public Map<String,Object> validate(@RequestParam MultipartFile file) throws IOException {
    return workbooks.validate(file.getBytes(),file.getOriginalFilename());
  }

  @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('CONTENT_MANAGER')")
  public Map<String,Object> importDraft(@RequestParam MultipartFile file,@AuthenticationPrincipal LearnerPrincipal actor) throws IOException {
    return workbooks.importDraft(file.getBytes(),file.getOriginalFilename(),actor.id());
  }

  private ResponseEntity<byte[]> file(String name,byte[] body) {
    return ResponseEntity.ok().contentType(XLSX).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"").body(body);
  }
}
