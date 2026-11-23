UPDATE notifications
SET is_read = FALSE
WHERE is_read IS NULL;

UPDATE notifications
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE notifications
    MODIFY COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notifications
    MODIFY COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notification_cleanup') = 0,
              'CREATE INDEX idx_notification_cleanup ON notifications(is_read, created_at, id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
