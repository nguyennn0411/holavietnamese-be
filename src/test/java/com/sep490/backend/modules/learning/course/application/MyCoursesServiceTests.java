package com.sep490.backend.modules.learning.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.modules.learning.course.domain.CourseStatus;
import com.sep490.backend.modules.learning.course.domain.model.Course;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MyCoursesServiceTests {
  @Test
  void excludesCancelledAndDroppedEnrollmentsAndCalculatesProgress() {
    var enrollments = mock(EnrollmentRepository.class);
    var courses = mock(CourseRepository.class);
    var service = new MyCoursesService(enrollments, courses);
    when(enrollments.findByUser(5L)).thenReturn(List.of(
        enrollment(1L, 10L, EnrollmentStatus.IN_PROGRESS),
        enrollment(2L, 11L, EnrollmentStatus.CANCELLED),
        enrollment(3L, 12L, EnrollmentStatus.DROPPED)));
    when(courses.findById(10L)).thenReturn(Optional.of(course(10L)));
    when(courses.countPublishedLessons(10L)).thenReturn(3);
    when(enrollments.completedLessons(1L)).thenReturn(2);

    var result = service.myCourses(5L);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().courseId()).isEqualTo(10L);
    assertThat(result.getFirst().progressPercentage()).isEqualTo(67);
    verify(courses, never()).findById(11L);
    verify(courses, never()).findById(12L);
  }

  private static Enrollment enrollment(long id, long course, EnrollmentStatus status) {
    return new Enrollment(id, 5L, course, Instant.EPOCH, status, null, null);
  }

  private static Course course(long id) {
    return new Course(id, "Course", "Description", "/media/course.png", "A1", 30,
        CourseStatus.PUBLISHED);
  }
}
