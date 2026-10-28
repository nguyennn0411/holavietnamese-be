package com.sep490.backend.modules.learning.controller.admin;

import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.service.CourseContentService;
import com.sep490.backend.modules.learning.service.CourseReviewService;
import com.sep490.backend.modules.learning.dto.request.ContentRequests;

import com.sep490.backend.common.security.LearnerPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTENT_MANAGER')")
public class ContentAdminController {
  private final CourseContentService courses;
  private final ActivityService activities;
  private final CourseReviewService workflow;

  @GetMapping("/courses")
  public Object list(@RequestParam Map<String, String> p, @AuthenticationPrincipal LearnerPrincipal u) {
    return courses.listForEditor(p, u.id(), workflow.isAdmin(u));
  }

  @PostMapping("/courses")
  public Object create(
      @Valid @RequestBody ContentRequests.Course r, @AuthenticationPrincipal LearnerPrincipal u) {
    return courses.detail(courses.create(r, u.id()), null, true);
  }

  @GetMapping("/courses/{id}")
  public Object detail(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanView(id,u);
    return courses.detail(id, null, true);
  }

  @GetMapping("/courses/{id}/readiness")
  public Object readiness(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanView(id,u);
    var issues = workflow.readiness(id);
    return Map.of("ready", issues.isEmpty(), "issues", issues);
  }

  @PutMapping("/courses/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody ContentRequests.Course r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(id,u);
    courses.edit(id, r);
    return courses.detail(id,null,true);
  }

  @PatchMapping("/courses/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public Object status(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanChangeStatus(id, r.status());
    courses.status(id, r.status());
    return courses.detail(id,null,true);
  }

  @PostMapping("/courses/{id}/submit")
  @PreAuthorize("hasRole('CONTENT_MANAGER')")
  public Object submit(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.submit(id,u);
    return courses.detail(id,null,true);
  }

  @PatchMapping("/courses/{id}/final-assessment")
  public Object finalAssessment(@PathVariable long id,
      @Valid @RequestBody ContentRequests.FinalAssessment r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(id,u);
    courses.setFinalAssessment(id,r.quizId());
    return courses.detail(id,null,true);
  }

  @PostMapping("/courses/{id}/review")
  @PreAuthorize("hasRole('ADMIN')")
  public Object review(@PathVariable long id, @Valid @RequestBody ContentRequests.CourseReview r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.review(id,r.decision(),r.note(),u);
    return courses.detail(id,null,true);
  }

  @PostMapping("/courses/{id}/modules")
  public Object module(@PathVariable long id, @Valid @RequestBody ContentRequests.Module r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(id,u);
    return Map.of("id", courses.addModule(id, r));
  }

  @PutMapping("/modules/{id}")
  public void moduleEdit(@PathVariable long id, @Valid @RequestBody ContentRequests.Module r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForModule(id),u);
    courses.editModule(id, r);
  }

  @DeleteMapping("/modules/{id}")
  public void moduleDelete(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForModule(id),u);
    courses.deleteModule(id);
  }

  @PutMapping("/courses/{id}/modules/reorder")
  public void modulesOrder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(id,u);
    courses.reorderModules(id, r.ids());
  }

  @GetMapping("/lessons")
  public Object lessons(@RequestParam Map<String, String> p, @AuthenticationPrincipal LearnerPrincipal u) {
    return courses.lessonsForEditor(p,u.id(),workflow.isAdmin(u));
  }

  @GetMapping("/lessons/{id}")
  public Object lesson(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanView(workflow.courseForLesson(id),u);
    return activities.lesson(id, null, true);
  }

  @GetMapping("/lessons/{id}/preview")
  public Object preview(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanView(workflow.courseForLesson(id),u);
    return activities.lesson(id, null, true);
  }

  @PostMapping("/modules/{id}/lessons")
  public Object addLesson(@PathVariable long id, @Valid @RequestBody ContentRequests.Lesson r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForModule(id),u);
    return Map.of("id", courses.addLesson(id, r));
  }

  @PutMapping("/lessons/{id}")
  public Object editLesson(@PathVariable long id, @Valid @RequestBody ContentRequests.Lesson r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForLesson(id),u);
    courses.editLesson(id, r);
    return activities.lesson(id,null,true);
  }

  @PatchMapping("/lessons/{id}/status")
  public Object lessonStatus(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForLesson(id),u);
    courses.lessonStatus(id, r.status());
    return activities.lesson(id,null,true);
  }

  @PutMapping("/modules/{id}/lessons/reorder")
  public void lessonsOrder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForModule(id),u);
    courses.reorderLessons(id, r.ids());
  }

  @PostMapping("/lessons/{id}/activities")
  public Object addActivity(@PathVariable long id, @Valid @RequestBody ContentRequests.Activity r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForLesson(id),u);
    return Map.of("id", activities.create(id, r));
  }

  @PutMapping("/activities/{id}")
  public void editActivity(@PathVariable long id, @Valid @RequestBody ContentRequests.Activity r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForActivity(id),u);
    activities.edit(id, r);
  }

  @DeleteMapping("/activities/{id}")
  public void deleteActivity(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForActivity(id),u);
    activities.delete(id);
  }

  @PutMapping("/lessons/{id}/activities/reorder")
  public void activitiesOrder(
      @PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    workflow.assertCanEdit(workflow.courseForLesson(id),u);
    activities.reorder(id, r.ids());
  }
}
