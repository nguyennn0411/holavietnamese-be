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

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.web.dto.ContentRequests;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseContentServiceTests {
  private ContentStore db;
  private CourseContentService service;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    service = new CourseContentService(db);
  }

  @Test
  void createCourseStartsAsDraftAndStoresVietnameseContent() {
    var request = course("A1", "/media/course.png");
    when(db.insert(anyString(), any(Object[].class))).thenReturn(41L);

    long id = service.create(request, 7L);

    assertThat(id).isEqualTo(41L);
    verify(db).insert(
        anyString(),
        eq("COURSE-A1"),
        eq("course-a1"),
        eq("Vietnamese A1"),
        eq("Description"),
        eq("A1"),
        eq("/media/course.png"),
        eq(60),
        eq("Outcome"),
        eq(true),
        eq(7L));
    verify(db).update(
        "UPDATE courses SET title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi) WHERE id=?",
        "Tiếng Việt A1",
        "Mô tả",
        41L);
  }

  @Test
  void createRejectsUnsupportedLevelAndUnsafeThumbnailUrl() {
    assertValidation(() -> service.create(course("BEGINNER", null), 7L), "Unsupported value");
    assertValidation(() -> service.create(course("A1", "file:///secret.png"), 7L), "Media URL");
    verify(db, never()).insert(anyString(), any(Object[].class));
  }

  @Test
  void publishingCourseRequiresAtLeastOnePublishedLesson() {
    when(db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", 41L))
        .thenReturn(0L);

    assertValidation(() -> service.status(41L, "PUBLISHED"), "at least one lesson");
    verify(db, never()).update(
        "UPDATE courses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", "PUBLISHED", 41L);
  }

  @Test
  void finalAssessmentMustBeCourseQuizFromSameCourse() {
    when(db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.one("SELECT course_id,quiz_type FROM quizzes WHERE id=?", 70L))
        .thenReturn(Map.of("courseId", 42L, "quizType", "COURSE"));
    assertValidation(() -> service.setFinalAssessment(41L, 70L), "from this course");

    when(db.one("SELECT course_id,quiz_type FROM quizzes WHERE id=?", 71L))
        .thenReturn(Map.of("courseId", 41L, "quizType", "LESSON"));
    assertValidation(() -> service.setFinalAssessment(41L, 71L), "course quiz");

    when(db.one("SELECT course_id,quiz_type FROM quizzes WHERE id=?", 72L))
        .thenReturn(Map.of("courseId", 41L, "quizType", "COURSE"));
    service.setFinalAssessment(41L, 72L);
    verify(db).update(
        "UPDATE courses SET final_assessment_quiz_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        72L,
        41L);
  }

  @Test
  void publishingLessonRequiresLearningActivityAndPublishedQuiz() {
    when(db.one("SELECT * FROM lessons WHERE id=?", 21L))
        .thenReturn(Map.of("id", 21L, "courseId", 41L));
    when(db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.count(
            "SELECT count(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED' AND"
                + " is_required=true",
            21L))
        .thenReturn(0L);
    assertValidation(() -> service.lessonStatus(21L, "PUBLISHED"), "required activity");

    when(db.count(
            "SELECT count(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED' AND"
                + " is_required=true",
            21L))
        .thenReturn(1L);
    when(db.count(anyString(), any(Object[].class))).thenReturn(1L);
    assertValidation(() -> service.lessonStatus(21L, "PUBLISHED"), "attached quizzes");
  }

  @Test
  void reorderModulesMustContainEveryCurrentModuleExactlyOnce() {
    when(db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", 41L)).thenReturn(Map.of("id", 41L));
    when(db.rows("SELECT id FROM course_units WHERE course_id=?", 41L))
        .thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L)));

    assertValidation(() -> service.reorderModules(41L, List.of(1L, 1L)), "exactly once");
    assertValidation(() -> service.reorderModules(41L, List.of(1L, 3L)), "exactly once");

    service.reorderModules(41L, List.of(2L, 1L));
    verify(db).update(anyString(), eq(1), eq(2L));
    verify(db).update(anyString(), eq(2), eq(1L));
  }

  private static ContentRequests.Course course(String level, String thumbnail) {
    return new ContentRequests.Course(
        "COURSE-A1",
        "course-a1",
        "Vietnamese A1",
        "Description",
        level,
        thumbnail,
        60,
        "Outcome",
        true,
        "Tiếng Việt A1",
        "Mô tả");
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
