-- Flyway Migration V13: Align AI tables with entity schema
-- Adds missing columns in ai_messages and ai_sessions

ALTER TABLE ai_messages ADD COLUMN name VARCHAR(100);
ALTER TABLE ai_messages ADD COLUMN avatar VARCHAR(255);
ALTER TABLE ai_messages ADD COLUMN native_alternative TEXT;
ALTER TABLE ai_messages ADD COLUMN cultural_note TEXT;

ALTER TABLE ai_sessions ADD COLUMN turns_count INT NOT NULL DEFAULT 0;
ALTER TABLE ai_sessions ADD COLUMN feedback_summary TEXT;
