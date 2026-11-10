package com.sep490.backend.modules.learning.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.learning.course.domain.CourseStatus;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.progress.application.LearningEventService;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseServiceTests {
  private CourseRepository courses;
  private EnrollmentRepository enrollments;
  private ContentStore db;
  private LearningEventService events;
  private CourseService service;
  private final Instant now = Instant.parse("2026-01-02T03:04:05Z");

  @BeforeEach
  void setUp() {
    courses = mock(CourseRepository.class);
    enrollments = mock(EnrollmentRepository.class);
    db = mock(ContentStore.class);
    events = mock(LearningEventService.class);
    service = new CourseService(
        courses, enrollments, Clock.fixed(now, ZoneOffset.UTC), db, events);
  }

  @Test
  void availableCoursesMapsPublishedLessonCounts() {
    when(courses.findAvailable()).thenReturn(List.of(course(1L), course(2L)));
    when(courses.countPublishedLessons(1L)).thenReturn(4);
    when(courses.countPublishedLessons(2L)).thenReturn(7);

    var result = service.availableCourses();

    assertThat(result).extracting("id").containsExactly(1L, 2L);
    assertThat(result).extracting("totalLessons").containsExactly(4, 7);
  }

  @Test
  void courseRejectsContentThatIsNotPublished() {
    when(db.count("SELECT count(*) FROM courses WHERE id=? AND status='PUBLISHED'", 9L))
        .thenReturn(0L);

    assertThatThrownBy(() -> service.course(9L))
        .isInstanceOf(LearningException.class)
        .hasMessageContaining("Available course");
    verify(courses, never()).findById(9L);
  }

  @Test
  void enrollingNewLearnerCreatesNotStartedEnrollmentAndLearningEvent() {
    stubAvailable(9L);
    when(db.one("SELECT id FROM users WHERE id=? FOR UPDATE", 3L)).thenReturn(Map.of("id", 3L));
    when(enrollments.findForUpdate(3L, 9L)).thenReturn(Optional.empty());
    when(db.count("SELECT count(*) FROM courses WHERE id=? AND slug IS NOT NULL", 9L))
        .thenReturn(1L);
    when(enrollments.save(any(Enrollment.class)))
        .thenAnswer(invocation -> {
          Enrollment input = invocation.getArgument(0);
          return new Enrollment(77L, input.userId(), input.courseId(), input.enrolledAt(),
              input.status(), null, null);
        });
    when(enrollments.completedLessons(77L)).thenReturn(0);
    when(courses.countPublishedLessons(9L)).thenReturn(5);

    var result = service.enroll(3L, 9L);

    assertThat(result.id()).isEqualTo(77L);
    assertThat(result.status()).isEqualTo(EnrollmentStatus.NOT_STARTED);
    assertThat(result.progressPercentage()).isZero();
    verify(events).record(3L, 9L, "COURSE_ENROLLED");
  }

  @Test
  void repeatedEnrollmentReturnsExistingActiveEnrollmentWithoutDuplicateWrite() {
    stubAvailable(9L);
    when(db.one("SELECT id FROM users WHERE id=? FOR UPDATE", 3L)).thenReturn(Map.of("id", 3L));
    Enrollment existing = enrollment(77L, EnrollmentStatus.IN_PROGRESS);
    when(enrollments.findForUpdate(3L, 9L)).thenReturn(Optional.of(existing));
    when(courses.countPublishedLessons(9L)).thenReturn(4);
    when(enrollments.completedLessons(77L)).thenReturn(2);

    var result = service.enroll(3L, 9L);

    assertThat(result.progressPercentage()).isEqualTo(50);
    verify(enrollments, never()).save(any());
    verifyNoInteractions(events);
  }

  @Test
  void cancelledCompletedEnrollmentResumesAsCompleted() {
    stubAvailable(9L);
    when(db.one("SELECT id FROM users WHERE id=? FOR UPDATE", 3L)).thenReturn(Map.of("id", 3L));
    when(enrollments.findForUpdate(3L, 9L))
        .thenReturn(Optional.of(enrollment(77L, EnrollmentStatus.CANCELLED)));
    when(courses.countPublishedLessons(9L)).thenReturn(3);
    when(enrollments.completedLessons(77L)).thenReturn(3);
    when(enrollments.save(any(Enrollment.class))).thenAnswer(i -> i.getArgument(0));

    assertThat(service.enroll(3L, 9L).status()).isEqualTo(EnrollmentStatus.COMPLETED);
    verify(events).record(3L, 9L, "COURSE_ENROLLED");
  }

  private void stubAvailable(long id) {
    when(db.count("SELECT count(*) FROM courses WHERE id=? AND status='PUBLISHED'", id))
        .thenReturn(1L);
    when(courses.findById(id)).thenReturn(Optional.of(course(id)));
  }

  private Course course(long id) {
    return new Course(id, "Vietnamese A1", "Description", null, "A1", 60,
        CourseStatus.PUBLISHED);
  }

  private Enrollment enrollment(long id, EnrollmentStatus status) {
    return new Enrollment(id, 3L, 9L, now.minusSeconds(100), status, null, null);
  }
}
