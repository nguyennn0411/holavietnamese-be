package com.sep490.backend.learning.course;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.shared.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ContentAdminController {
  private final CourseContentService courses;
  private final ActivityService activities;

  @GetMapping("/courses")
  public Object list(@RequestParam Map<String, String> p) {
    return courses.list(p, true);
  }

  @PostMapping("/courses")
  public Object create(
      @Valid @RequestBody ContentRequests.Course r, @AuthenticationPrincipal LearnerPrincipal u) {
    return courses.detail(courses.create(r, u.id()), null, true);
  }

  @GetMapping("/courses/{id}")
  public Object detail(@PathVariable long id) {
    return courses.detail(id, null, true);
  }

  @PutMapping("/courses/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody ContentRequests.Course r) {
    courses.edit(id, r);
    return detail(id);
  }

  @PatchMapping("/courses/{id}/status")
  public Object status(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r) {
    courses.status(id, r.status());
    return detail(id);
  }

  @PostMapping("/courses/{id}/modules")
  public Object module(@PathVariable long id, @Valid @RequestBody ContentRequests.Module r) {
    return Map.of("id", courses.addModule(id, r));
  }

  @PutMapping("/modules/{id}")
  public void moduleEdit(@PathVariable long id, @Valid @RequestBody ContentRequests.Module r) {
    courses.editModule(id, r);
  }

  @DeleteMapping("/modules/{id}")
  public void moduleDelete(@PathVariable long id) {
    courses.deleteModule(id);
  }

  @PutMapping("/courses/{id}/modules/reorder")
  public void modulesOrder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r) {
    courses.reorderModules(id, r.ids());
  }

  @GetMapping("/lessons")
  public Object lessons(@RequestParam Map<String, String> p) {
    return courses.lessons(p);
  }

  @GetMapping("/lessons/{id}")
  public Object lesson(@PathVariable long id) {
    return activities.lesson(id, null, true);
  }

  @GetMapping("/lessons/{id}/preview")
  public Object preview(@PathVariable long id) {
    return activities.lesson(id, null, true);
  }

  @PostMapping("/modules/{id}/lessons")
  public Object addLesson(@PathVariable long id, @Valid @RequestBody ContentRequests.Lesson r) {
    return Map.of("id", courses.addLesson(id, r));
  }

  @PutMapping("/lessons/{id}")
  public Object editLesson(@PathVariable long id, @Valid @RequestBody ContentRequests.Lesson r) {
    courses.editLesson(id, r);
    return lesson(id);
  }

  @PatchMapping("/lessons/{id}/status")
  public Object lessonStatus(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r) {
    courses.lessonStatus(id, r.status());
    return lesson(id);
  }

  @PutMapping("/modules/{id}/lessons/reorder")
  public void lessonsOrder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r) {
    courses.reorderLessons(id, r.ids());
  }

  @PostMapping("/lessons/{id}/activities")
  public Object addActivity(@PathVariable long id, @Valid @RequestBody ContentRequests.Activity r) {
    return Map.of("id", activities.create(id, r));
  }

  @PutMapping("/activities/{id}")
  public void editActivity(@PathVariable long id, @Valid @RequestBody ContentRequests.Activity r) {
    activities.edit(id, r);
  }

  @DeleteMapping("/activities/{id}")
  public void deleteActivity(@PathVariable long id) {
    activities.delete(id);
  }

  @PutMapping("/lessons/{id}/activities/reorder")
  public void activitiesOrder(
      @PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r) {
    activities.reorder(id, r.ids());
  }
}
