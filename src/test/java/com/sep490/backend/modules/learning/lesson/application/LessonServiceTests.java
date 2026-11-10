package com.sep490.backend.modules.learning.lesson.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.learning.authoring.application.CourseContentService;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.lesson.domain.LessonStatus;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonRepository;
import com.sep490.backend.modules.learning.progress.application.LearnerAccess;
import com.sep490.backend.modules.learning.progress.application.LearningEventService;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.domain.model.LessonProgress;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.LessonProgressRepository;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LessonServiceTests {
  private LessonRepository lessons;
  private LessonProgressRepository progress;
  private EnrollmentRepository enrollments;
  private CourseRepository courses;
  private LearnerAccess access;
  private ContentStore db;
  private ActivityProgressService activityProgress;
  private CourseContentService contentCourses;
  private LearningEventService events;
  private LessonService service;
  private final Instant now = Instant.parse("2026-01-02T03:04:05Z");

  @BeforeEach
  void setUp() {
    lessons = mock(LessonRepository.class);
    progress = mock(LessonProgressRepository.class);
    enrollments = mock(EnrollmentRepository.class);
    courses = mock(CourseRepository.class);
    access = mock(LearnerAccess.class);
    db = mock(ContentStore.class);
    activityProgress = mock(ActivityProgressService.class);
    contentCourses = mock(CourseContentService.class);
    events = mock(LearningEventService.class);
    service = new LessonService(lessons, progress, enrollments, courses, access,
        Clock.fixed(now, ZoneOffset.UTC), db, activityProgress, contentCourses, events);
  }

  @Test
  void lessonListMapsProgressAndNavigationWithoutLeakingContent() {
    when(access.requireEnrollment(3L, 9L, false)).thenReturn(enrollment(EnrollmentStatus.IN_PROGRESS));
    when(lessons.findPublishedByCourse(9L)).thenReturn(List.of(lesson(21L, 1), lesson(22L, 2)));
    when(progress.findByEnrollment(7L)).thenReturn(List.of(
        new LessonProgress(50L, 7L, 21L, LessonStatus.COMPLETED, now, now)));

    var result = service.lessons(3L, 9L);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().learningStatus()).isEqualTo(LessonStatus.COMPLETED);
    assertThat(result.getFirst().content()).isNull();
    assertThat(result.getFirst().nextLessonId()).isEqualTo(22L);
    assertThat(result.get(1).previousLessonId()).isEqualTo(21L);
  }

  @Test
  void lockedOrUnpublishedLessonCannotBeOpened() {
    when(contentCourses.locked(3L, 21L)).thenReturn(true);
    assertThatThrownBy(() -> service.lesson(3L, 21L))
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo("LESSON_LOCKED"));

    when(contentCourses.locked(3L, 22L)).thenReturn(false);
    when(lessons.findById(22L)).thenReturn(Optional.of(
        new Lesson(22L, 9L, "Draft", "", 2, "Secret", null, null, 5, false)));
    assertThatThrownBy(() -> service.lesson(3L, 22L))
        .isInstanceOf(LearningException.class);
  }

  @Test
  void completingLegacyLessonCreatesProgressAndCourseCompletionEvents() {
    Lesson lesson = lesson(21L, 1);
    when(contentCourses.locked(3L, 21L)).thenReturn(false);
    when(db.count("SELECT count(*) FROM lesson_activities WHERE lesson_id=?", 21L)).thenReturn(0L);
    when(lessons.findById(21L)).thenReturn(Optional.of(lesson));
    when(access.requireEnrollment(3L, 9L, true)).thenReturn(enrollment(EnrollmentStatus.ACTIVE));
    when(progress.find(7L, 21L)).thenReturn(Optional.empty());
    when(progress.save(any(LessonProgress.class))).thenAnswer(invocation -> {
      LessonProgress value = invocation.getArgument(0);
      return new LessonProgress(50L, value.enrollmentId(), value.lessonId(), value.status(),
          value.startedAt(), value.completedAt());
    });
    when(courses.countPublishedLessons(9L)).thenReturn(1);
    when(enrollments.completedLessons(7L)).thenReturn(1);
    when(enrollments.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));
    when(lessons.findPublishedByCourse(9L)).thenReturn(List.of(lesson));

    var result = service.complete(3L, 21L);

    assertThat(result.learningStatus()).isEqualTo(LessonStatus.COMPLETED);
    verify(events).record(3L, 21L, "LESSON_COMPLETED");
    verify(events).record(3L, 9L, "COURSE_COMPLETED");
  }

  @Test
  void activityBasedLessonCannotBeForceCompletedUntilRequiredWorkIsDone() {
    when(contentCourses.locked(3L, 21L)).thenReturn(false);
    when(db.count("SELECT count(*) FROM lesson_activities WHERE lesson_id=?", 21L)).thenReturn(2L);
    when(activityProgress.evaluate(3L, 21L)).thenReturn(java.util.Map.of("completed", false));

    assertThatThrownBy(() -> service.complete(3L, 21L))
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo("LESSON_INCOMPLETE"));
    verify(activityProgress).startLesson(3L, 21L);
  }

  private Enrollment enrollment(EnrollmentStatus status) {
    return new Enrollment(7L, 3L, 9L, now.minusSeconds(60), status, null, null);
  }

  private static Lesson lesson(long id, int order) {
    return new Lesson(id, 9L, "Lesson " + order, "Description", order,
        "Content", null, null, 5, true);
  }
}
