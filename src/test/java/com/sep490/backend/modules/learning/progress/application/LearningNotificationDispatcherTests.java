package com.sep490.backend.modules.learning.progress.application;

import static org.mockito.Mockito.*;

import com.sep490.backend.common.notification.NotificationProperties;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LearningNotificationDispatcherTests {
  private ContentStore db;
  private LearningNotificationProcessor processor;
  private NotificationProperties properties;
  private LearningNotificationDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    db = mock(ContentStore.class);
    processor = mock(LearningNotificationProcessor.class);
    properties = new NotificationProperties();
    dispatcher = new LearningNotificationDispatcher(db, processor, properties);
  }

  @Test
  void disabledNotificationsSkipPendingQuery() {
    properties.setEnabled(false);
    dispatcher.dispatchPending();
    verifyNoInteractions(db, processor);
  }

  @Test
  void dispatchesEveryPendingEventUsingBoundedBatchSize() {
    properties.setLearningEventBatchSize(1000);
    when(db.rows(anyString(), eq(500)))
        .thenReturn(new ArrayList<>(List.of(
            Map.of("eventKey", "LESSON_COMPLETED:3:21"),
            Map.of("eventKey", "COURSE_COMPLETED:3:9"))));

    dispatcher.dispatchPending();

    verify(processor).process("LESSON_COMPLETED:3:21");
    verify(processor).process("COURSE_COMPLETED:3:9");
  }

  @Test
  void eventListenerAndRetryContinueWhenProcessorFails() {
    doThrow(new RuntimeException("temporary")).when(processor).process("EVENT-1");
    dispatcher.onLearningEventCreated(new LearningEventCreated("EVENT-1"));
    verify(processor).process("EVENT-1");
  }
}
