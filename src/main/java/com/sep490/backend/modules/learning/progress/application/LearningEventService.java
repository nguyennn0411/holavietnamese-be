package com.sep490.backend.modules.learning.progress.application;

import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.sql.Timestamp;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningEventService {

    private final ContentStore db;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public boolean record(long userId, long entityId, String eventType) {
        String normalizedType = eventType == null ? "" : eventType.trim().toUpperCase();
        if (normalizedType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        String eventKey = normalizedType + ":" + userId + ":" + entityId;
        if (db.count("SELECT count(*) FROM learning_events WHERE event_key=?", eventKey) > 0) {
            return false;
        }

        db.insert(
                "INSERT INTO learning_events(event_key,event_type,user_id,entity_id,occurred_at)"
                        + " VALUES(?,?,?,?,?)",
                eventKey,
                normalizedType,
                userId,
                entityId,
                Timestamp.from(clock.instant()));
        publisher.publishEvent(new LearningEventCreated(eventKey));
        return true;
    }
}
