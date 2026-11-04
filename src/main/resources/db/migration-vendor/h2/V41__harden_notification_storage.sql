UPDATE notifications
SET is_read = FALSE
WHERE is_read IS NULL;

UPDATE notifications
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE notifications ALTER COLUMN is_read SET DEFAULT FALSE;
ALTER TABLE notifications ALTER COLUMN is_read SET NOT NULL;

ALTER TABLE notifications ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE notifications ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX idx_notification_cleanup
    ON notifications(is_read, created_at, id);
