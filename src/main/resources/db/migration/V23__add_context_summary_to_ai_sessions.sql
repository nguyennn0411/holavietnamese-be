-- Flyway Migration V23: Add context_summary to ai_sessions for conversation memory
ALTER TABLE ai_sessions ADD COLUMN context_summary TEXT;
