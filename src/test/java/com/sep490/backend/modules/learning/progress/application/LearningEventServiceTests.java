package com.sep490.backend.modules.learning.progress.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class LearningEventServiceTests {
  private ContentStore db;
  private ApplicationEventPublisher publisher;
  private LearningEventService service;
  private final Instant now = Instant.parse("2026-01-02T03:04:05Z");

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    publisher = mock(ApplicationEventPublisher.class);
    service = new LearningEventService(db, Clock.fixed(now, ZoneOffset.UTC), publisher);
  }

  @Test
  void recordsNormalizedIdempotentEventAndPublishesIt() {
    when(db.count("SELECT count(*) FROM learning_events WHERE event_key=?",
        "LESSON_COMPLETED:3:21")).thenReturn(0L);

    assertThat(service.record(3L, 21L, " lesson_completed ")).isTrue();
    verify(db).insert(anyString(), eq("LESSON_COMPLETED:3:21"), eq("LESSON_COMPLETED"),
        eq(3L), eq(21L), eq(Timestamp.from(now)));
    verify(publisher).publishEvent(new LearningEventCreated("LESSON_COMPLETED:3:21"));
  }

  @Test
  void duplicateEventIsIgnored() {
    when(db.count(anyString(), eq("COURSE_ENROLLED:3:9"))).thenReturn(1L);
    assertThat(service.record(3L, 9L, "COURSE_ENROLLED")).isFalse();
    verify(db, never()).insert(anyString(), any(Object[].class));
    verifyNoInteractions(publisher);
  }

  @Test
  void blankEventTypeIsRejected() {
    assertThatThrownBy(() -> service.record(3L, 9L, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventType");
  }
}
