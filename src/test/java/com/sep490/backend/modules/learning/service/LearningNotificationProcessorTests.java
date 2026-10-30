package com.sep490.backend.modules.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.store.ContentStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningNotificationProcessorTests {

    @Mock
    private ContentStore db;

    @Mock
    private NotificationPort notifications;

    @Mock
    private AchievementService achievementService;

    @Mock
    private UserRepository userRepository;

    private LearningNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        NotificationProperties props = new NotificationProperties();
        props.setEnabled(true);
        processor = new LearningNotificationProcessor(
                db,
                notifications,
                props,
                Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC),
                achievementService,
                userRepository);
    }

    @Test
    void courseCompletionCreatesOneIdempotentAchievementNotification() {
        String eventKey = "COURSE_COMPLETED:7:3";
        when(db.rows(contains("FROM learning_events"), eq(eventKey)))
                .thenReturn(List.of(event(eventKey, "COURSE_COMPLETED", 7L, 3L)));
        when(db.rows(contains("FROM courses"), eq(3L)))
                .thenReturn(List.of(Map.of("title", "Tiếng Việt Du Lịch")));

        processor.process(eventKey);

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).sendOnce(eq(7L), captor.capture(), eq(eventKey));
        assertThat(captor.getValue().title()).contains("hoàn thành khóa học");
        assertThat(captor.getValue().linkUrl()).isEqualTo("/learn/3");
        verify(db).update(contains("UPDATE learning_events SET delivered_at=?"), any(Timestamp.class), eq(eventKey));
    }

    @Test
    void unsupportedEventIsIgnoredWithoutDeliveredTimestamp() {
        String eventKey = "UNKNOWN:1:1";
        when(db.rows(contains("FROM learning_events"), eq(eventKey)))
                .thenReturn(List.of(event(eventKey, "UNKNOWN", 1L, 1L)));

        processor.process(eventKey);

        verify(notifications, never()).sendOnce(any(), any(), any());
        verify(db, never()).update(contains("UPDATE learning_events SET delivered_at=?"), any(), any());
    }

    @Test
    void lessonCompletionSkippedIfCourseAlreadyCompleted() {
        String eventKey = "LESSON_COMPLETED:4:12";
        when(db.rows(contains("FROM learning_events"), eq(eventKey)))
                .thenReturn(List.of(event(eventKey, "LESSON_COMPLETED", 4L, 12L)));
        when(db.rows(contains("FROM lessons"), eq(12L)))
                .thenReturn(List.of(Map.of("title", "Bài 1", "courseId", 99L)));
        when(db.count(contains("FROM learning_events WHERE event_type=? AND user_id=? AND entity_id=?"),
                eq("COURSE_COMPLETED"), eq(4L), eq(99L)))
                .thenReturn(1L);

        processor.process(eventKey);

        verify(notifications, never()).sendOnce(any(), any(), any());
        verify(db).update(contains("UPDATE learning_events SET delivered_at=?"), any(Timestamp.class), eq(eventKey));
    }

    private static Map<String, Object> event(String eventKey, String type, long userId, long entityId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventKey", eventKey);
        row.put("eventType", type);
        row.put("userId", userId);
        row.put("entityId", entityId);
        return row;
    }
}
