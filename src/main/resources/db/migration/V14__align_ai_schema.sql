-- Flyway Migration V14: Align AI tables with entity schema
-- Adds missing columns in ai_messages and ai_sessions

ALTER TABLE ai_messages
    ADD COLUMN name VARCHAR(100) AFTER sender,
    ADD COLUMN avatar VARCHAR(255) AFTER name,
    ADD COLUMN native_alternative TEXT AFTER explanation,
    ADD COLUMN cultural_note TEXT AFTER native_alternative;

ALTER TABLE ai_sessions
    ADD COLUMN turns_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN feedback_summary TEXT AFTER rating;

