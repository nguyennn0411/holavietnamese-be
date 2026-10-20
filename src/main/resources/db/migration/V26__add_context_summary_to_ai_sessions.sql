-- Add conversation memory after the already applied V1-V25 migrations.
-- Safe conditional execution so it never fails if context_summary already exists.
SET @dbname = DATABASE();
SET @tablename = 'ai_sessions';
SET @columnname = 'context_summary';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname
       AND TABLE_NAME = @tablename
       AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE ai_sessions ADD COLUMN context_summary TEXT'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
