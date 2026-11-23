-- V35: Add course/lesson context to AI sessions and FULLTEXT index on lessons
ALTER TABLE ai_sessions ADD COLUMN course_id BIGINT NULL;
ALTER TABLE ai_sessions ADD COLUMN lesson_id BIGINT NULL;
ALTER TABLE ai_sessions ADD COLUMN coach_mode VARCHAR(50) NULL;

ALTER TABLE ai_sessions ADD CONSTRAINT fk_ai_session_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE SET NULL;
ALTER TABLE ai_sessions ADD CONSTRAINT fk_ai_session_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE SET NULL;
