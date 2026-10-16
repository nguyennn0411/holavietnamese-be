-- Flyway Migration V13: Align AI tables with entity schema
-- Adds missing columns in ai_messages and ai_sessions

ALTER TABLE ai_messages ADD COLUMN name VARCHAR(100) AFTER sender;
ALTER TABLE ai_messages ADD COLUMN avatar VARCHAR(255) AFTER name;
ALTER TABLE ai_messages ADD COLUMN native_alternative TEXT AFTER explanation;
ALTER TABLE ai_messages ADD COLUMN cultural_note TEXT AFTER native_alternative;

ALTER TABLE ai_sessions ADD COLUMN turns_count INT NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE ai_sessions ADD COLUMN feedback_summary TEXT AFTER rating;
