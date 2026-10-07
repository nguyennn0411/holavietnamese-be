package com.sep490.backend.learning.quiz;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.shared.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class QuizController {
  private final QuizService quizzes;
  private final QuestionBankService questions;

  @GetMapping("/api/admin/questions")
  public Object questions(@RequestParam Map<String, String> p) {
    return questions.list(p);
  }

  @GetMapping("/api/admin/questions/{id}")
  public Object question(@PathVariable long id) {
    return questions.detail(id);
  }

  @PostMapping("/api/admin/questions")
  public Object createQuestion(@Valid @RequestBody ContentRequests.Question r) {
    return question(questions.create(r));
  }

  @RequestMapping(
      value = {"/api/admin/questions/{id}"},
      method = RequestMethod.PUT)
  public Object editQuestion(
      @PathVariable long id, @Valid @RequestBody ContentRequests.Question r) {
    questions.edit(id, r);
    return question(id);
  }

  @PostMapping("/api/admin/questions/{id}/versions")
  public Object version(@PathVariable long id, @Valid @RequestBody ContentRequests.Question r) {
    return editQuestion(id, r);
  }

  @GetMapping("/api/admin/quizzes")
  public Object quizzes(@RequestParam Map<String, String> p) {
    return quizzes.list(p);
  }

  @PostMapping("/api/admin/quizzes")
  public Object create(@Valid @RequestBody ContentRequests.Quiz r) {
    return quizzes.detail(quizzes.create(r), true);
  }

  @GetMapping("/api/admin/quizzes/{id}")
  public Object adminQuiz(@PathVariable long id) {
    return quizzes.detail(id, true);
  }

  @PutMapping("/api/admin/quizzes/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody ContentRequests.Quiz r) {
    quizzes.edit(id, r);
    return adminQuiz(id);
  }

  @PatchMapping("/api/admin/quizzes/{id}/status")
  public Object status(@PathVariable long id, @Valid @RequestBody ContentRequests.Status r) {
    quizzes.status(id, r.status());
    return adminQuiz(id);
  }

  @GetMapping("/api/admin/quizzes/{id}/preview")
  public Object preview(@PathVariable long id) {
    return quizzes.preview(id);
  }

  @PostMapping("/api/admin/quizzes/{id}/questions")
  public void add(@PathVariable long id, @Valid @RequestBody ContentRequests.QuizQuestion r) {
    quizzes.addQuestion(id, r);
  }

  @DeleteMapping("/api/admin/quizzes/{id}/questions/{question}")
  public void remove(@PathVariable long id, @PathVariable long question) {
    quizzes.removeQuestion(id, question);
  }

  @PutMapping("/api/admin/quizzes/{id}/questions/reorder")
  public void reorder(@PathVariable long id, @Valid @RequestBody ContentRequests.Reorder r) {
    quizzes.reorder(id, r.ids());
  }

  @GetMapping("/api/admin/quiz-attempts")
  public Object attempts(@RequestParam Map<String, String> p) {
    return quizzes.attempts(p);
  }

  @GetMapping("/api/admin/quiz-attempts/{id}")
  public Object adminAttempt(@PathVariable long id) {
    return quizzes.attempt(0, id, true);
  }

  @GetMapping("/api/admin/quizzes/{id}/analytics")
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
