-- Keep this migration portable across the production MySQL database and the
-- H2 database used by integration tests. Flyway applies each version once, so
-- procedural, database-specific conditional SQL is unnecessary here.
ALTER TABLE ai_sessions ADD COLUMN context_summary TEXT;
