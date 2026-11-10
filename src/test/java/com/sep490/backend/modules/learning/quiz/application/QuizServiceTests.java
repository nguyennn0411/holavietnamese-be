package com.sep490.backend.modules.learning.quiz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.lesson.application.ActivityProgressService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizServiceTests {
  private ContentStore db;
  private QuestionScoring scoring;
  private ActivityProgressService progress;
  private QuizService service;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    scoring = mock(QuestionScoring.class);
    progress = mock(ActivityProgressService.class);
    service = new QuizService(db, scoring, progress,
        Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
  }

  @Test
  void createsLessonQuizForMatchingCourse() {
    when(db.one("SELECT course_id FROM lessons WHERE id=?", 21L)).thenReturn(Map.of("courseId", 9L));
    when(db.one("SELECT id FROM courses WHERE id=?", 9L)).thenReturn(Map.of("id", 9L));
    when(db.insert(anyString(), any(Object[].class))).thenReturn(41L);

    long id = service.create(quiz("LESSON", 9L, 21L));

    assertThat(id).isEqualTo(41L);
    verify(db).insert(anyString(), eq("Quiz"), eq("Description"), eq("LESSON"), eq(9L),
        eq(21L), eq(BigDecimal.valueOf(70)), eq(10), eq(2), eq(false));
    verify(db).update(anyString(), eq("Bài kiểm tra"), eq("Mô tả"), eq(41L));
  }

  @Test
  void rejectsLessonQuizWithoutLessonOrFromDifferentCourse() {
    assertValidation(() -> service.create(quiz("LESSON", 9L, null)), "requires a lesson");
    when(db.one("SELECT course_id FROM lessons WHERE id=?", 21L)).thenReturn(Map.of("courseId", 10L));
    assertValidation(() -> service.create(quiz("LESSON", 9L, 21L)), "lesson's course");
  }

  @Test
  void publishingQuizRequiresAtLeastOnePublishedValidQuestion() {
    when(db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.rows(anyString(), eq(41L))).thenReturn(List.of());

    assertValidation(() -> service.status(41L, "PUBLISHED"), "at least one question");

    when(db.rows(anyString(), eq(42L))).thenReturn(List.of(Map.of(
        "status", "DRAFT", "questionType", "MULTIPLE_CHOICE",
        "optionsSnapshotJson", "[]", "correctAnswerJson", "{}")));
    when(db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", 42L)).thenReturn(Map.of("id", 42L));
    assertValidation(() -> service.status(42L, "PUBLISHED"), "Publish every selected question");
  }

  @Test
  void addingExistingQuestionUpdatesPointsAndBumpsQuizVersion() {
    when(db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.one("SELECT id FROM questions WHERE id=?", 70L)).thenReturn(Map.of("id", 70L));
    when(db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=? AND question_id=?", 41L, 70L))
        .thenReturn(1L);

    service.addQuestion(41L, new ContentRequests.QuizQuestion(70L, BigDecimal.TEN));

    verify(db).update("UPDATE quiz_questions SET points=? WHERE quiz_id=? AND question_id=?",
        BigDecimal.TEN, 41L, 70L);
    verify(db).update("UPDATE quizzes SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", 41L);
  }

  @Test
  void publishedQuizCannotLoseItsLastQuestion() {
    when(db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=?", 41L)).thenReturn(1L);
    when(db.one("SELECT status FROM quizzes WHERE id=?", 41L)).thenReturn(Map.of("status", "PUBLISHED"));

    assertValidation(() -> service.removeQuestion(41L, 70L), "keep at least one question");
    verify(db, never()).update("DELETE FROM quiz_questions WHERE quiz_id=? AND question_id=?", 41L, 70L);
  }

  @Test
  void learnerCannotStartUnpublishedQuiz() {
    when(db.one("SELECT * FROM quizzes WHERE id=? FOR UPDATE", 41L))
        .thenReturn(Map.of("id", 41L, "status", "DRAFT"));

    assertThatThrownBy(() -> service.start(3L, 41L))
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo("QUIZ_NOT_PUBLISHED"));
    verify(progress).lockUser(3L);
  }

  private static ContentRequests.Quiz quiz(String type, Long course, Long lesson) {
    return new ContentRequests.Quiz("Quiz", "Description", type, course, lesson,
        BigDecimal.valueOf(70), 10, 2, false, "Bài kiểm tra", "Mô tả");
  }

  private static void assertValidation(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ContentException.class, error -> {
          assertThat(error.code).isEqualTo("VALIDATION_ERROR");
          assertThat(error).hasMessageContaining(message);
        });
  }
}
