package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.learning.course.domain.CourseStatus;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LearnerAccessTests {
  private CourseRepository courses;
  private EnrollmentRepository enrollments;
  private LearnerAccess access;

  @BeforeEach
  void setUp() {
    courses = mock(CourseRepository.class);
    enrollments = mock(EnrollmentRepository.class);
    access = new LearnerAccess(courses, enrollments);
  }

  @Test
  void returnsActiveEnrollmentAndUsesLockingReadWhenRequested() {
    when(courses.findById(9L)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
    Enrollment enrollment = enrollment(EnrollmentStatus.IN_PROGRESS);
    when(enrollments.findForUpdate(3L, 9L)).thenReturn(Optional.of(enrollment));

    assertThat(access.requireEnrollment(3L, 9L, true)).isSameAs(enrollment);
    verify(enrollments).findForUpdate(3L, 9L);
    verify(enrollments, never()).find(3L, 9L);
  }

  @Test
  void archivedCourseRemainsAvailableToExistingLearner() {
    when(courses.findById(9L)).thenReturn(Optional.of(course(CourseStatus.ARCHIVED)));
    when(enrollments.find(3L, 9L)).thenReturn(Optional.of(enrollment(EnrollmentStatus.COMPLETED)));

    assertThat(access.requireEnrollment(3L, 9L, false).status())
        .isEqualTo(EnrollmentStatus.COMPLETED);
  }

  @Test
  void rejectsDraftCourseAndCancelledEnrollment() {
    when(courses.findById(9L)).thenReturn(Optional.of(course(CourseStatus.DRAFT)));
    assertThatThrownBy(() -> access.requireEnrollment(3L, 9L, false))
        .isInstanceOfSatisfying(LearningException.class,
            error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.NOT_FOUND));

    when(courses.findById(9L)).thenReturn(Optional.of(course(CourseStatus.PUBLISHED)));
    when(enrollments.find(3L, 9L)).thenReturn(Optional.of(enrollment(EnrollmentStatus.CANCELLED)));
    assertThatThrownBy(() -> access.requireEnrollment(3L, 9L, false))
        .isInstanceOfSatisfying(LearningException.class,
            error -> assertThat(error.kind()).isEqualTo(LearningException.Kind.FORBIDDEN));
  }

  private static Course course(CourseStatus status) {
    return new Course(9L, "Course", "Description", null, "A1", 30, status);
  }

  private static Enrollment enrollment(EnrollmentStatus status) {
    return new Enrollment(7L, 3L, 9L, Instant.EPOCH, status, null, null);
  }
}
