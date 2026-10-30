package com.sep490.backend.modules.learning.service;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.learning.store.ContentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class LearningNotificationDispatcher {

    private final ContentStore db;
    private final LearningNotificationProcessor processor;
    private final NotificationProperties properties;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onLearningEventCreated(LearningEventCreated event) {
        processSafely(event.eventKey());
    }

    /** Retry durable events that could not be delivered after their original transaction. */
    @Scheduled(fixedDelayString = "${app.notification.learning-event-poll-interval:30s}")
    public void dispatchPending() {
        if (!properties.isEnabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(500, properties.getLearningEventBatchSize()));
        var events = db.rows(
                "SELECT event_key FROM learning_events WHERE delivered_at IS NULL"
                        + " AND event_type IN ('COURSE_ENROLLED','COURSE_COMPLETED',"
                        + "'LESSON_COMPLETED','QUIZ_PASSED','QUIZ_PERFECT_SCORE')"
                        + " ORDER BY id LIMIT ?",
                batchSize);
        events.forEach(event -> processSafely(String.valueOf(event.get("eventKey"))));
    }

    private void processSafely(String eventKey) {
        try {
            processor.process(eventKey);
        } catch (RuntimeException exception) {
            log.warn("Chưa thể tạo thông báo cho learning event {}: {}",
                    eventKey, exception.getMessage());
        }
    }
}
