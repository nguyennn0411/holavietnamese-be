-- Keep this migration portable across MySQL and TiDB. Flyway applies each
-- version once, so procedural, database-specific conditional SQL is unnecessary.
ALTER TABLE ai_sessions ADD COLUMN context_summary TEXT;
