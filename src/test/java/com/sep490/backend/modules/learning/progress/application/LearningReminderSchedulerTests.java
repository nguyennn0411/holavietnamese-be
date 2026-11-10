package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningReminderSchedulerTests {

    @Mock ContentStore db;
    @Mock NotificationPort notifications;

    @Test
    void createsOneDailyReminderForAnInactiveEnrollment() {
        NotificationProperties properties = new NotificationProperties();
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
        LearningReminderScheduler scheduler = new LearningReminderScheduler(
                db, notifications, properties, clock);
        when(db.rows(any(String.class), any(), any()))
                .thenReturn(List.of(Map.of("userId", 7L, "courseId", 12L, "courseTitle", "Vietnamese A1")));
        when(notifications.sendOnce(eq(7L), any(NotificationMessage.class), eq("LEARNING_REMINDER:7:2026-11-03")))
                .thenReturn(true);

        scheduler.dispatchDailyReminders();

        ArgumentCaptor<NotificationMessage> message = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notifications).sendOnce(eq(7L), message.capture(), eq("LEARNING_REMINDER:7:2026-11-03"));
        assertThat(message.getValue().linkUrl()).isEqualTo("/learn/12");
        assertThat(message.getValue().message()).contains("Vietnamese A1");
    }
}
