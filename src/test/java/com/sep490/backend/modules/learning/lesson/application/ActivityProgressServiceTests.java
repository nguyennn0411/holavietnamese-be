package com.sep490.backend.modules.learning.lesson.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.modules.learning.authoring.application.CourseContentService;
import com.sep490.backend.modules.learning.progress.application.LearningEventService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityProgressServiceTests {
  private ContentStore db;
  private CourseContentService courses;
  private LearningEventService events;
  private ActivityProgressService service;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    courses = mock(CourseContentService.class);
    events = mock(LearningEventService.class);
    service = new ActivityProgressService(db, courses,
        Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC), events);
  }

  @Test
  void accessRejectsUnpublishedLessonMissingEnrollmentAndLockedLesson() {
    when(db.one(anyString(), eq(21L))).thenReturn(lesson(false));
    assertCode(() -> service.access(3L, 21L, false), "LESSON_NOT_PUBLISHED");

    when(db.one(anyString(), eq(22L))).thenReturn(lesson(true));
    when(db.rows(anyString(), eq(3L), eq(9L))).thenReturn(List.of());
    assertCode(() -> service.access(3L, 22L, false), "ENROLLMENT_REQUIRED");

    when(db.one(anyString(), eq(23L))).thenReturn(lesson(true));
    when(db.rows(anyString(), eq(3L), eq(9L)))
        .thenReturn(new ArrayList<>(List.of(Map.of("id", 7L))));
    when(courses.locked(3L, 23L)).thenReturn(true);
    assertCode(() -> service.access(3L, 23L, false), "LESSON_LOCKED");
  }

  @Test
  void successfulAccessAddsEnrollmentIdentity() {
    Map<String, Object> lesson = lesson(true);
    when(db.one(anyString(), eq(21L))).thenReturn(lesson);
    when(db.rows(anyString(), eq(3L), eq(9L)))
        .thenReturn(new ArrayList<>(List.of(Map.of("id", 7L))));
    when(courses.locked(3L, 21L)).thenReturn(false);

    assertThat(service.access(3L, 21L, false)).containsEntry("enrollmentId", 7L);
  }

  @Test
  void startLessonCreatesProgressOnceAndUpdatesEnrollment() {
    Map<String, Object> lesson = lesson(true);
    when(db.one(anyString(), eq(21L))).thenReturn(lesson);
    when(db.rows(anyString(), eq(3L), eq(9L)))
        .thenReturn(new ArrayList<>(List.of(Map.of("id", 7L))));
    when(courses.locked(3L, 21L)).thenReturn(false);
    when(db.count(
        "SELECT count(*) FROM lesson_progress WHERE enrollment_id=? AND lesson_id=?", 7L, 21L))
        .thenReturn(0L);

    service.startLesson(3L, 21L);

    verify(db).insert(startsWith("INSERT INTO lesson_progress"), eq(7L), eq(21L), any(), any());
    verify(db).update(startsWith("UPDATE enrollments SET status=CASE"),
        any(), any(), eq(21L), eq(7L));
  }

  @Test
  void courseProgressCalculatesPercentage() {
    Map<String, Object> enrollment = new LinkedHashMap<>(Map.of("id", 7L, "status", "IN_PROGRESS"));
    when(db.one(anyString(), eq(3L), eq(9L))).thenReturn(enrollment);
    when(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", 9L))
        .thenReturn(4L);
    when(db.count(anyString(), eq(7L))).thenReturn(3L);

    assertThat(service.courseProgress(3L, 9L))
        .containsEntry("totalLessons", 4L)
        .containsEntry("completedLessons", 3L)
        .containsEntry("progressPercentage", 75L);
  }

  private static Map<String, Object> lesson(boolean published) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", 21L);
    row.put("courseId", 9L);
    row.put("published", published);
    row.put("courseStatus", "PUBLISHED");
    return row;
  }

  private static void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo(code));
  }
}
