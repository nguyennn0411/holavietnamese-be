package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.sep490.backend.modules.learning.lesson.domain.LessonStatus;
import com.sep490.backend.modules.learning.lesson.domain.model.Lesson;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonRepository;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.domain.model.LessonProgress;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.LessonProgressRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningProgressServiceTests {
  @Test
  void combinesLessonStatesAndMarksSummaryCompleted() {
    var access = mock(LearnerAccess.class);
    var lessons = mock(LessonRepository.class);
    var progress = mock(LessonProgressRepository.class);
    var service = new LearningProgressService(access, lessons, progress);
    var enrollment = new Enrollment(7L, 3L, 9L, Instant.EPOCH,
        EnrollmentStatus.IN_PROGRESS, 22L, Instant.EPOCH.plusSeconds(5));
    when(access.requireEnrollment(3L, 9L, false)).thenReturn(enrollment);
    when(lessons.findPublishedByCourse(9L)).thenReturn(List.of(lesson(21L), lesson(22L)));
    when(progress.findByEnrollment(7L)).thenReturn(List.of(
        state(21L, LessonStatus.COMPLETED), state(22L, LessonStatus.COMPLETED)));

    var result = service.progress(3L, 9L);

    assertThat(result.totalLessons()).isEqualTo(2);
    assertThat(result.completedLessons()).isEqualTo(2);
    assertThat(result.progressPercentage()).isEqualTo(100);
    assertThat(result.status()).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(result.lessons()).extracting("status")
        .containsExactly(LessonStatus.COMPLETED, LessonStatus.COMPLETED);
  }

  @Test
  void missingProgressDefaultsLessonToNotStartedAndKeepsEnrollmentStatus() {
    var access = mock(LearnerAccess.class);
    var lessons = mock(LessonRepository.class);
    var progress = mock(LessonProgressRepository.class);
    var service = new LearningProgressService(access, lessons, progress);
    when(access.requireEnrollment(3L, 9L, false)).thenReturn(
        new Enrollment(7L, 3L, 9L, Instant.EPOCH, EnrollmentStatus.ACTIVE, null, null));
    when(lessons.findPublishedByCourse(9L)).thenReturn(List.of(lesson(21L)));
    when(progress.findByEnrollment(7L)).thenReturn(List.of());

    var result = service.progress(3L, 9L);

    assertThat(result.progressPercentage()).isZero();
    assertThat(result.status()).isEqualTo(EnrollmentStatus.ACTIVE);
    assertThat(result.lessons().getFirst().status()).isEqualTo(LessonStatus.NOT_STARTED);
  }

  private static Lesson lesson(long id) {
    return new Lesson(id, 9L, "Lesson", "Description", (int) id, "Content", null, null, 5, true);
  }

  private static LessonProgress state(long lesson, LessonStatus status) {
    return new LessonProgress(lesson, 7L, lesson, status, Instant.EPOCH,
        status == LessonStatus.COMPLETED ? Instant.EPOCH.plusSeconds(10) : null);
  }
}
