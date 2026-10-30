package com.sep490.backend.modules.learning.quiz.web;
import com.sep490.backend.modules.learning.authoring.application.CourseReviewService;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.quiz.application.QuestionBankService;
import com.sep490.backend.modules.learning.quiz.application.QuizService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;




import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.common.exception.ContentException;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class QuizController {
  private final QuizService quizzes;
  private final QuestionBankService questions;
  private final CourseReviewService workflow;
  private final ContentStore db;

  private void questionView(long id, LearnerPrincipal user) {
    if (!workflow.isAdmin(user)) questions.assertCanView(id, user.id());
  }

  private void questionEdit(long id, LearnerPrincipal user) {
    if (!workflow.isAdmin(user)) questions.assertCanEdit(id, user.id());
    questions.assertNotInReviewedCourse(id);
  }

  private long quizCourse(long id) {
    var quiz = db.one("SELECT course_id FROM quizzes WHERE id=?", id);
    if (quiz.get("courseId") == null)
      throw ContentException.forbidden("COURSE_REQUIRED", "Content managers can only work on course quizzes.");
    return ContentStore.id(quiz, "courseId");
  }

  private void quizView(long id, LearnerPrincipal user) {
    if (!workflow.isAdmin(user)) workflow.assertCanView(quizCourse(id), user);
  }

  private void quizEdit(long id, LearnerPrincipal user) {
    var quiz = db.one("SELECT course_id FROM quizzes WHERE id=?", id);
    if (quiz.get("courseId") == null) {
      if (!workflow.isAdmin(user)) quizCourse(id);
    } else workflow.assertCanEdit(ContentStore.id(quiz, "courseId"), user);
  }

  @GetMapping("/api/admin/questions")
  public Object questions(@RequestParam Map<String, String> p, @AuthenticationPrincipal LearnerPrincipal user) {
    return questions.listForEditor(p, workflow.isAdmin(user) ? null : user.id());
  }

  @GetMapping("/api/admin/questions/{id}")
  public Object question(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal user) {
    questionView(id, user);
    return questions.detail(id);
  }

  @PostMapping("/api/admin/questions")
  public Object createQuestion(@Valid @RequestBody ContentRequests.Question r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    long id = questions.create(r, user.id());
    return questions.detail(id);
  }

  @RequestMapping(
      value = {"/api/admin/questions/{id}"},
      method = RequestMethod.PUT)
  public Object editQuestion(
      @PathVariable long id, @Valid @RequestBody ContentRequests.Question r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    questionEdit(id, user);
    questions.edit(id, r);
    return questions.detail(id);
  }

  @PostMapping("/api/admin/questions/{id}/versions")
  public Object version(@PathVariable long id, @Valid @RequestBody ContentRequests.Question r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    return editQuestion(id, r, user);
  }

  @GetMapping("/api/admin/quizzes")
  public Object quizzes(@RequestParam Map<String, String> p, @AuthenticationPrincipal LearnerPrincipal user) {
    return quizzes.listForEditor(p, workflow.isAdmin(user) ? null : user.id());
  }

  @PostMapping("/api/admin/quizzes")
  public Object create(@Valid @RequestBody ContentRequests.Quiz r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    if (!workflow.isAdmin(user) && r.courseId() == null)
      throw ContentException.invalid("Select a course before creating a quiz.");
    if (r.courseId() != null) workflow.assertCanEdit(r.courseId(), user);
    return quizzes.detail(quizzes.create(r), true);
  }

  @GetMapping("/api/admin/quizzes/{id}")
  public Object adminQuiz(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal user) {
    quizView(id, user);
    return quizzes.detail(id, true);
  }

  @PutMapping("/api/admin/quizzes/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody ContentRequests.Quiz r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    quizEdit(id, user);
    if (!workflow.isAdmin(user) && (r.courseId() == null || r.courseId() != quizCourse(id)))
      throw ContentException.invalid("Quiz must stay in its course.");
    quizzes.edit(id, r);
    return quizzes.detail(id, true);
  }

  @PatchMapping("/api/admin/quizzes/{id}/status")
  public Object status(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    quizEdit(id, user);
    quizzes.status(id, r.status());
    return quizzes.detail(id, true);
  }

  @GetMapping("/api/admin/quizzes/{id}/preview")
  public Object preview(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal user) {
    quizView(id, user);
    return quizzes.preview(id);
  }

  @PostMapping("/api/admin/quizzes/{id}/questions")
  public void add(@PathVariable long id, @Valid @RequestBody ContentRequests.QuizQuestion r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    quizEdit(id, user);
    questionView(r.questionId(), user);
    quizzes.addQuestion(id, r);
  }

  @DeleteMapping("/api/admin/quizzes/{id}/questions/{question}")
  public void remove(@PathVariable long id, @PathVariable long question,
      @AuthenticationPrincipal LearnerPrincipal user) {
    quizEdit(id, user);
    quizzes.removeQuestion(id, question);
  }

  @PutMapping("/api/admin/quizzes/{id}/questions/reorder")
  public void reorder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r,
      @AuthenticationPrincipal LearnerPrincipal user) {
    quizEdit(id, user);
    quizzes.reorder(id, r.ids());
  }

  @GetMapping("/api/admin/quiz-attempts")
  @PreAuthorize("hasRole('ADMIN')")
  public Object attempts(@RequestParam Map<String, String> p) {
    return quizzes.attempts(p);
  }

  @GetMapping("/api/admin/quiz-attempts/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public Object adminAttempt(@PathVariable long id) {
    return quizzes.attempt(0, id, true);
  }

  @GetMapping("/api/admin/quizzes/{id}/analytics")
  @PreAuthorize("hasRole('ADMIN')")
  public Object analytics(@PathVariable long id) {
    return quizzes.analytics(id);
  }

  @GetMapping("/api/quizzes/{id}")
  public Object quiz(@PathVariable long id) {
    return quizzes.detail(id, false);
  }

  @PostMapping({"/api/quizzes/{id}/start", "/api/quizzes/{id}/retry"})
  public Object start(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return quizzes.start(u.id(), id);
  }

  @GetMapping("/api/quiz-attempts/{id}")
  public Object attempt(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return quizzes.attempt(u.id(), id, false);
  }

  @PutMapping("/api/quiz-attempts/{id}/answers/{question}")
  public void answer(
      @PathVariable long id,
      @PathVariable long question,
      @Valid @RequestBody ContentRequests.Answer r,
      @AuthenticationPrincipal LearnerPrincipal u) {
    quizzes.save(u.id(), id, question, r.answer());
  }

  @PostMapping("/api/quiz-attempts/{id}/submit")
  public Object submit(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return quizzes.submit(u.id(), id);
  }

  @GetMapping("/api/quiz-attempts/{id}/result")
  public Object result(@PathVariable long id, @AuthenticationPrincipal LearnerPrincipal u) {
    return quizzes.result(u.id(), id);
  }
}
