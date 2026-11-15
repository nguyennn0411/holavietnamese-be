ALTER TABLE notifications ADD COLUMN event_key VARCHAR(200) NULL;

CREATE UNIQUE INDEX uk_notification_user_event
    ON notifications(user_id, event_key);
CREATE INDEX idx_notification_user_created
    ON notifications(user_id, created_at, id);
CREATE INDEX idx_notification_user_read_created
    ON notifications(user_id, is_read, created_at, id);

-- Do not flood existing users with historical events when the dispatcher is first deployed.
UPDATE learning_events
SET delivered_at = CURRENT_TIMESTAMP
WHERE delivered_at IS NULL
  AND event_type IN (
      'COURSE_ENROLLED',
      'COURSE_COMPLETED',
      'LESSON_COMPLETED',
      'QUIZ_PASSED',
      'QUIZ_PERFECT_SCORE'
  );
