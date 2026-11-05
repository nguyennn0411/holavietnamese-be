-- Idempotent re-application of V12__course_review_workflow.sql.
-- Some databases recorded version 12 for an older migration before renumbering,
-- so the course review workflow columns and foreign keys were never added there.

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'submitted_by') = 0,
              'ALTER TABLE courses ADD COLUMN submitted_by BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'submitted_at') = 0,
              'ALTER TABLE courses ADD COLUMN submitted_at TIMESTAMP(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'reviewed_by') = 0,
              'ALTER TABLE courses ADD COLUMN reviewed_by BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'reviewed_at') = 0,
              'ALTER TABLE courses ADD COLUMN reviewed_at TIMESTAMP(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND COLUMN_NAME = 'review_note') = 0,
              'ALTER TABLE courses ADD COLUMN review_note VARCHAR(2000) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND CONSTRAINT_NAME = 'fk_course_submitted_by') = 0,
              'ALTER TABLE courses ADD CONSTRAINT fk_course_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'courses' AND CONSTRAINT_NAME = 'fk_course_reviewed_by') = 0,
              'ALTER TABLE courses ADD CONSTRAINT fk_course_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
