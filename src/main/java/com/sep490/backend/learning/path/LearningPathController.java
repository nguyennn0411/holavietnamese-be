package com.sep490.backend.learning.path;

import com.sep490.backend.config.LearnerPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning-path")
@RequiredArgsConstructor
@PreAuthorize("hasRole('LEARNER')")
public class LearningPathController {
  private final LearningPathService paths;

  @GetMapping("/me")
  public Object me(@AuthenticationPrincipal LearnerPrincipal user) {
    return paths.me(user.id());
  }

  @GetMapping("/recommendation")
  public Object recommendation(@AuthenticationPrincipal LearnerPrincipal user) {
    return paths.recommendation(user.id());
  }

  @PostMapping("/apply")
  public Object apply(@Valid @RequestBody LearningPathRequests.Apply request,
      @AuthenticationPrincipal LearnerPrincipal user) {
    var courseIds = request.courseIds() == null || request.courseIds().isEmpty()
        ? request.courseId() == null ? java.util.List.<Long>of() : java.util.List.of(request.courseId())
        : request.courseIds();
    return paths.apply(user.id(), courseIds);
  }

  @PatchMapping("/preferences")
  public Object preferences(@Valid @RequestBody LearningPathRequests.Preferences request,
      @AuthenticationPrincipal LearnerPrincipal user) {
    return paths.updatePreferences(user.id(), request);
  }
}
