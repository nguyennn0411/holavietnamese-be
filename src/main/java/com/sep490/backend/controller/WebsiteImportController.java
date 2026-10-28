package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.service.websiteimport.*;
import com.sep490.backend.service.websiteimport.WebsiteImportModel.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/website-imports")
@PreAuthorize("hasRole('ADMIN')")
public class WebsiteImportController {
  private final WebsiteImportService service;

  @PostMapping("/analyze")
  public ApiResponse<Project> analyze(@Valid @RequestBody AnalyzeRequest request,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return ApiResponse.success(service.analyze(request, actor));
  }

  @PostMapping(value="/analyze-file", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Project> analyzeFile(@RequestParam MultipartFile file,
      @RequestParam(required=false) Long targetCourseId,
      @AuthenticationPrincipal LearnerPrincipal actor) throws IOException {
    return ApiResponse.success(service.analyzeFile(file.getBytes(), file.getOriginalFilename(), targetCourseId, actor));
  }

  @GetMapping("/{id}") public ApiResponse<Project> detail(@PathVariable String id) { return ApiResponse.success(service.detail(id)); }
  @GetMapping public ApiResponse<List<Project>> history(@RequestParam(defaultValue="20") int limit) { return ApiResponse.success(service.history(limit)); }

  @PutMapping("/{id}/draft")
  public ApiResponse<Project> update(@PathVariable String id, @Valid @RequestBody UpdateRequest request,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return ApiResponse.success(service.update(id, request, actor));
  }

  @PostMapping("/{id}/retry-media")
  public ApiResponse<Project> retryMedia(@PathVariable String id,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return ApiResponse.success(service.retryMedia(id, actor));
  }

  @PostMapping("/{id}/save")
  public ApiResponse<Project> save(@PathVariable String id, @Valid @RequestBody SaveRequest request,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return ApiResponse.success(service.save(id, request, actor));
  }
}
