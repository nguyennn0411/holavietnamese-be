package com.sep490.backend.modules.learning.importing.controller;

import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.importing.service.LessonImageImportService;
import com.sep490.backend.modules.learning.importing.service.LessonImageImportService.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/content/courses/{courseId}/lesson-images")
@PreAuthorize("hasRole('CONTENT_CREATOR')")
public class LessonImageImportController {
  private final LessonImageImportService images;

  public record Preview(@NotNull @Size(min = 1, max = 100)
                        List<@NotNull @Valid Item> items) {}

  @PostMapping("/preview")
  public List<LessonImageImportService.Target> preview(
      @PathVariable long courseId, @RequestBody @Valid Preview request,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return images.preview(courseId, request.items(), actor);
  }

  @PutMapping
  public Map<String, LessonImageImportService.Action> attach(
      @PathVariable long courseId, @RequestBody @Valid LessonImageImportService.Image image,
      @AuthenticationPrincipal LearnerPrincipal actor) {
    return Map.of("action", images.attach(courseId, image, actor));
  }
}
