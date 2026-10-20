-- Add conversation memory after the already applied V1-V25 migrations.
ALTER TABLE ai_sessions ADD COLUMN context_summary TEXT;
