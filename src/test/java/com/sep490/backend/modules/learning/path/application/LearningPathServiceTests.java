package com.sep490.backend.modules.learning.path.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.common.exception.ContentException;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.modules.learning.course.application.CourseService;
import com.sep490.backend.modules.learning.path.web.dto.LearningPathRequests;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LearningPathServiceTests {
  private ContentStore db;
  private CourseService enrollments;
  private NotificationPort notifications;
  private LearningPathService service;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    enrollments = mock(CourseService.class);
    notifications = mock(NotificationPort.class);
    service = new LearningPathService(db, enrollments, notifications);
  }

  @Test
  void levelAndSourceMustBeUpdatedTogether() {
    var missingSource = new LearningPathRequests.Preferences("A1", null, List.of("travel"), 15, null);
    var missingLevel = new LearningPathRequests.Preferences(null, "PROFILE", List.of(), 15, null);

    assertThatThrownBy(() -> service.updatePreferences(3L, missingSource))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cùng nhau");
    assertThatThrownBy(() -> service.updatePreferences(3L, missingLevel))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void applyRequiresAtLeastOnePublishedCourse() {
    assertThatThrownBy(() -> service.apply(3L, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ít nhất một khóa học");

    when(db.rows(anyString(), eq(99L))).thenReturn(List.of());
    assertThatThrownBy(() -> service.apply(3L, List.of(99L)))
        .isInstanceOfSatisfying(ContentException.class,
            error -> assertThat(error.code).isEqualTo("COURSE_NOT_PUBLISHED"));
    verifyNoInteractions(enrollments);
  }

  @Test
  void recommendationWithoutResolvedLevelRequestsPlacementInsteadOfGuessing() {
    when(db.one("SELECT target_level,learning_goal,daily_learning_goal_minutes FROM users WHERE id=?", 3L))
        .thenReturn(Map.of("learningGoal", ""));
    when(db.rows("SELECT * FROM learning_path_preferences WHERE user_id=?", 3L))
        .thenReturn(List.of());

    var result = service.recommendation(3L);

    assertThat(result).containsEntry("requiresLevel", true)
        .containsEntry("recommendedCourse", null);
    assertThat((List<?>) result.get("courses")).isEmpty();
  }
}
