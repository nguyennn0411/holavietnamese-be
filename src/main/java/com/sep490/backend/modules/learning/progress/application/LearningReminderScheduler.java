package com.sep490.backend.modules.learning.progress.application;

import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LearningReminderScheduler {

    private final ContentStore db;
    private final NotificationPort notifications;
    private final NotificationProperties properties;
    private final Clock clock;

    @Scheduled(
            cron = "${app.notification.learning-reminder-cron:0 0 19 * * *}",
            zone = "${app.notification.learning-reminder-zone:Asia/Ho_Chi_Minh}")
    public void dispatchDailyReminders() {
        if (!properties.isLearningReminderEnabled() || !properties.isEnabled()) {
            return;
        }

        Instant inactiveSince = clock.instant()
                .minusSeconds(Math.max(1, properties.getLearningReminderInactiveHours()) * 3_600L);
        int batchSize = Math.max(1, Math.min(1_000, properties.getLearningReminderBatchSize()));
        var candidates = db.rows("""
                SELECT e.user_id AS user_id, e.course_id AS course_id, c.title AS course_title
                FROM enrollments e
                JOIN users u ON u.id = e.user_id
                JOIN courses c ON c.id = e.course_id
                WHERE e.status IN ('ACTIVE', 'NOT_STARTED', 'IN_PROGRESS')
                  AND u.is_removed = false
                  AND u.enabled = true
                  AND UPPER(u.status) = 'ACTIVE'
                  AND COALESCE(u.notifications_enabled, true) = true
                  AND COALESCE(e.last_accessed_at, e.enrolled_at) < ?
                ORDER BY COALESCE(e.last_accessed_at, e.enrolled_at), e.id
                LIMIT ?
                """, Timestamp.from(inactiveSince), batchSize);

        LocalDate reminderDate = LocalDate.now(ZoneId.of(properties.getLearningReminderZone()));
        int sent = 0;
        for (Map<String, Object> candidate : candidates) {
            long userId = ContentStore.id(candidate, "userId");
            long courseId = ContentStore.id(candidate, "courseId");
            String courseTitle = String.valueOf(candidate.get("courseTitle"));
            boolean created = notifications.sendOnce(userId, NotificationMessage.standard(
                    "Ready for today's Vietnamese lesson?",
                    "Continue \"" + courseTitle + "\" and keep your learning streak going.",
                    NotificationType.LESSON,
                    "/learn/" + courseId), "LEARNING_REMINDER:" + userId + ":" + reminderDate);
            if (created) {
                sent++;
            }
        }
        log.info("Created {} daily learning reminders", sent);
    }
}
