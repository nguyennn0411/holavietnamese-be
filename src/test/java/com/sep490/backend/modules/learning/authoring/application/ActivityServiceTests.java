package com.sep490.backend.modules.learning.authoring.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.lesson.application.ActivityProgressService;
import com.sep490.backend.modules.learning.quiz.application.QuestionScoring;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityServiceTests {
  private ContentStore db;
  private CourseContentService courses;
  private ActivityService service;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    courses = mock(CourseContentService.class);
    service = new ActivityService(
        db, mock(ActivityProgressService.class), courses, mock(QuestionScoring.class));
    when(db.one("SELECT * FROM lessons WHERE id=?", 21L))
        .thenReturn(Map.of("id", 21L, "courseId", 41L));
  }

  @Test
  void createsPublishedTextActivityInNextPosition() throws Exception {
    JsonNode content = json.readTree("{\"body\":\"Xin chào\"}");
    when(db.encode(content)).thenReturn("{\"body\":\"Xin chào\"}");
    when(db.count(
            "SELECT COALESCE(MAX(order_index),0)+1 FROM lesson_activities WHERE lesson_id=?", 21L))
        .thenReturn(3L);
    when(db.insert(anyString(), any(Object[].class))).thenReturn(51L);

    long id = service.create(21L, activity("TEXT", content, true, "PUBLISHED", null, List.of()));

    assertThat(id).isEqualTo(51L);
    verify(courses).lock(41L);
    verify(db).insert(
        anyString(),
        eq(21L),
        eq("TEXT"),
        eq("Activity"),
        eq("Instruction"),
        eq("{\"body\":\"Xin chào\"}"),
        eq(3L),
        eq(true),
        eq(BigDecimal.ZERO),
        eq("PUBLISHED"),
        eq(null));
  }

  @Test
  void rejectsUnsafeMediaUrlAndNonObjectContent() throws Exception {
    JsonNode unsafe = json.readTree("{\"imageUrl\":\"file:///secret.png\"}");
    when(db.encode(unsafe)).thenReturn(unsafe.toString());
    assertValidation(
        () -> service.create(21L, activity("IMAGE", unsafe, false, "DRAFT", null, List.of())),
        "Media URL");

    JsonNode array = json.readTree("[1,2,3]");
    assertValidation(
        () -> service.create(21L, activity("TEXT", array, false, "DRAFT", null, List.of())),
        "JSON object");
  }

  @Test
  void requiredExternalAiActivityCannotBePublishedBeforeIntegrationIsVerified() throws Exception {
    JsonNode content = json.readTree("{}");
    when(db.encode(content)).thenReturn("{}");

    assertValidation(
        () -> service.create(
            21L, activity("AI_ROLEPLAY", content, true, "PUBLISHED", null, List.of())),
        "external integration");
  }

  @Test
  void quizActivityRequiresQuizFromSameLessonAndPublishedStatus() throws Exception {
    JsonNode content = json.readTree("{}");
    when(db.encode(content)).thenReturn("{}");
    assertValidation(
        () -> service.create(21L, activity("QUIZ", content, true, "DRAFT", null, List.of())),
        "Select a quiz");

    when(db.one("SELECT * FROM quizzes WHERE id=?", 70L))
        .thenReturn(Map.of("id", 70L, "lessonId", 22L, "status", "PUBLISHED"));
    assertValidation(
        () -> service.create(21L, activity("QUIZ", content, true, "DRAFT", 70L, List.of())),
        "this lesson");

    when(db.one("SELECT * FROM quizzes WHERE id=?", 71L))
        .thenReturn(Map.of("id", 71L, "lessonId", 21L, "status", "DRAFT"));
    assertValidation(
        () -> service.create(21L, activity("QUIZ", content, true, "PUBLISHED", 71L, List.of())),
        "Publish the quiz first");
  }

  @Test
  void grammarActivityRequiresTopicsWithoutDuplicates() throws Exception {
    JsonNode content = json.readTree("{}");
    when(db.encode(content)).thenReturn("{}");
    assertValidation(
        () -> service.create(21L, activity("GRAMMAR", content, true, "DRAFT", null, List.of())),
        "at least one grammar topic");
    assertValidation(
        () -> service.create(21L, activity("GRAMMAR", content, true, "DRAFT", null, List.of(5L, 5L))),
        "Duplicate grammar topics");
  }

  @Test
  void deletingActivityWithProgressArchivesItInsteadOfRemovingHistory() {
    when(db.one("SELECT * FROM lesson_activities WHERE id=? FOR UPDATE", 51L))
        .thenReturn(Map.of("id", 51L));
    when(db.count("SELECT count(*) FROM activity_progress WHERE activity_id=?", 51L))
        .thenReturn(2L);

    service.delete(51L);

    verify(db).update("UPDATE lesson_activities SET status='ARCHIVED' WHERE id=?", 51L);
    verify(db, never()).update("DELETE FROM lesson_activities WHERE id=?", 51L);
  }

  @Test
  void questionTypeNormalizesListeningAndGenericPractice() throws Exception {
    assertThat(ActivityService.questionType("LISTEN_AND_CHOOSE", json.readTree("{}")))
        .isEqualTo("LISTENING");
    assertThat(ActivityService.questionType(
            "PRACTICE", json.readTree("{\"questionType\":\"MATCHING\"}")))
        .isEqualTo("MATCHING");
    assertThat(ActivityService.questionType("FILL_BLANK", json.readTree("{}")))
        .isEqualTo("FILL_BLANK");
  }

  private static ContentRequests.Activity activity(
      String type,
      JsonNode content,
      boolean required,
      String status,
      Long quiz,
      List<Long> grammarTopics) {
    return new ContentRequests.Activity(
        type,
        "Activity",
        "Instruction",
        content,
        required,
        BigDecimal.ZERO,
        status,
        quiz,
        grammarTopics,
        "Hoạt động",
        "Hướng dẫn");
  }

  private static void assertValidation(Runnable action, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ContentException.class,
            error -> {
              assertThat(error.code).isEqualTo("VALIDATION_ERROR");
              assertThat(error).hasMessageContaining(message);
            });
  }
}
