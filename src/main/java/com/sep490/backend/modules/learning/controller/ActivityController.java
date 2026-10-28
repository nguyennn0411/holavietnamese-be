package com.sep490.backend.modules.learning.controller;

import com.sep490.backend.modules.learning.service.ActivityProgressService;
import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.dto.request.ContentRequests;

import com.sep490.backend.common.security.LearnerPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ActivityController {
  private final ActivityService activities;
  private final ActivityProgressService progress;

  @GetMapping("/api/lessons/{id}/activities")
  public Object list(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return activities.activities(id, u.id(), false);
  }

  @GetMapping("/api/lessons/{id}/progress")
  public Object lessonProgress(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return progress.lessonProgress(u.id(), id);
  }

  @PostMapping("/api/activities/{id}/start")
  public Object start(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return activities.interact(u.id(), id, null, false);
  }

  @PostMapping("/api/activities/{id}/complete")
  public Object complete(
      @PathVariable long id,
      @RequestBody(required = false) ContentRequests.Answer r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    return activities.interact(u.id(), id, r == null ? null : r.answer(), true);
  }

  @GetMapping("/api/courses/{id}/progress")
  public Object courseProgress(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return progress.courseProgress(u.id(), id);
  }

  @GetMapping("/api/users/me/courses")
  public Object enrolled(
      @RequestParam Map<String, String> p, @AuthenticationPrincipal LearnerPrincipal u) {
    return progress.myCourses(u.id(), p);
  }
}
