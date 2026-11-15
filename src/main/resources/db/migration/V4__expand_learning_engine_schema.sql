ALTER TABLE courses ADD slug VARCHAR(200);
ALTER TABLE courses ADD learning_outcomes TEXT;
ALTER TABLE courses ADD is_free BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE courses ADD created_by BIGINT;
ALTER TABLE courses ADD created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE courses ADD updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
CREATE UNIQUE INDEX uk_courses_slug ON courses(slug);
CREATE INDEX idx_courses_catalog ON courses(status,level);
ALTER TABLE course_units ADD status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE course_units ADD created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE course_units ADD updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
CREATE INDEX idx_units_order ON course_units(course_id,sort_order);
ALTER TABLE lessons ADD slug VARCHAR(200);
ALTER TABLE lessons ADD lesson_type VARCHAR(30) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE lessons ADD created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE lessons ADD updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
CREATE UNIQUE INDEX uk_lessons_slug ON lessons(slug);
CREATE INDEX idx_lessons_module_order ON lessons(unit_id,unit_sort_order);
ALTER TABLE enrollments ADD started_at TIMESTAMP(6);
ALTER TABLE enrollments ADD completed_at TIMESTAMP(6);
ALTER TABLE lesson_progress ADD progress_percent INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD best_score DECIMAL(10,2);
ALTER TABLE lesson_progress ADD last_accessed_at TIMESTAMP(6);
UPDATE lesson_progress SET progress_percent=100 WHERE status='COMPLETED';

CREATE TABLE lesson_prerequisites (
 lesson_id BIGINT NOT NULL, prerequisite_lesson_id BIGINT NOT NULL,
 PRIMARY KEY(lesson_id,prerequisite_lesson_id),
 FOREIGN KEY(lesson_id) REFERENCES lessons(id),
 FOREIGN KEY(prerequisite_lesson_id) REFERENCES lessons(id),
 CHECK(lesson_id<>prerequisite_lesson_id)
);
CREATE TABLE grammar_topics (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(80) NOT NULL UNIQUE, slug VARCHAR(200) NOT NULL UNIQUE,
 title VARCHAR(200) NOT NULL, structure_pattern VARCHAR(1000) NOT NULL, explanation TEXT NOT NULL,
 common_mistakes TEXT, level VARCHAR(10) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_grammar_catalog ON grammar_topics(level,status);
CREATE TABLE grammar_examples (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, grammar_topic_id BIGINT NOT NULL,
 vietnamese_text VARCHAR(2000) NOT NULL, translation VARCHAR(2000) NOT NULL, explanation TEXT,
 order_index INT NOT NULL, FOREIGN KEY(grammar_topic_id) REFERENCES grammar_topics(id)
);
CREATE TABLE questions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, question_type VARCHAR(40) NOT NULL, prompt TEXT NOT NULL,
 explanation TEXT, difficulty VARCHAR(10) NOT NULL, topic VARCHAR(200), image_url VARCHAR(2048), audio_url VARCHAR(2048),
 status VARCHAR(20) NOT NULL, current_version INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX idx_questions_filter ON questions(question_type,difficulty,status);
CREATE TABLE question_versions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, question_id BIGINT NOT NULL, version_number INT NOT NULL,
 question_type VARCHAR(40) NOT NULL, prompt TEXT NOT NULL, explanation TEXT, difficulty VARCHAR(10) NOT NULL,
 image_url VARCHAR(2048), audio_url VARCHAR(2048), correct_answer_json TEXT NOT NULL, options_snapshot_json TEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(question_id,version_number), FOREIGN KEY(question_id) REFERENCES questions(id)
);
CREATE TABLE quizzes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, description TEXT,
 quiz_type VARCHAR(20) NOT NULL, course_id BIGINT, lesson_id BIGINT,
 passing_score DECIMAL(5,2) NOT NULL, time_limit_minutes INT, max_attempts INT,
 randomize_questions BOOLEAN NOT NULL DEFAULT FALSE, status VARCHAR(20) NOT NULL, version INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(course_id) REFERENCES courses(id), FOREIGN KEY(lesson_id) REFERENCES lessons(id)
);
CREATE TABLE quiz_questions (
 quiz_id BIGINT NOT NULL, question_id BIGINT NOT NULL, order_index INT NOT NULL, points DECIMAL(10,2) NOT NULL,
 PRIMARY KEY(quiz_id,question_id), UNIQUE(quiz_id,order_index),
 FOREIGN KEY(quiz_id) REFERENCES quizzes(id), FOREIGN KEY(question_id) REFERENCES questions(id), CHECK(points>0)
);
CREATE TABLE lesson_activities (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, lesson_id BIGINT NOT NULL,
 activity_type VARCHAR(40) NOT NULL, title VARCHAR(200) NOT NULL, instruction TEXT, content_json TEXT NOT NULL,
 order_index INT NOT NULL, is_required BOOLEAN NOT NULL DEFAULT TRUE, max_score DECIMAL(10,2) NOT NULL DEFAULT 0,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', quiz_id BIGINT,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE(lesson_id,order_index), FOREIGN KEY(lesson_id) REFERENCES lessons(id), FOREIGN KEY(quiz_id) REFERENCES quizzes(id)
);
CREATE TABLE activity_grammar_topics (
 activity_id BIGINT NOT NULL, grammar_topic_id BIGINT NOT NULL, order_index INT NOT NULL,
 PRIMARY KEY(activity_id,grammar_topic_id), FOREIGN KEY(activity_id) REFERENCES lesson_activities(id),
 FOREIGN KEY(grammar_topic_id) REFERENCES grammar_topics(id)
);
CREATE TABLE activity_progress (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, activity_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, progress_percent INT NOT NULL DEFAULT 0, score DECIMAL(10,2), attempts INT NOT NULL DEFAULT 0,
 answer_json TEXT, last_attempt_at TIMESTAMP(6), completed_at TIMESTAMP(6),
 UNIQUE(user_id,activity_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(activity_id) REFERENCES lesson_activities(id)
);
CREATE TABLE quiz_attempts (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, quiz_id BIGINT NOT NULL, user_id BIGINT NOT NULL, quiz_version INT NOT NULL,
 quiz_title VARCHAR(200) NOT NULL, passing_score DECIMAL(5,2) NOT NULL, expires_at TIMESTAMP(6),
 status VARCHAR(20) NOT NULL, score DECIMAL(10,2), max_score DECIMAL(10,2) NOT NULL,
 percentage DECIMAL(5,2), passed BOOLEAN, started_at TIMESTAMP(6) NOT NULL, submitted_at TIMESTAMP(6),
 FOREIGN KEY(quiz_id) REFERENCES quizzes(id), FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_attempt_user_quiz ON quiz_attempts(user_id,quiz_id);
CREATE INDEX idx_attempt_quiz_date ON quiz_attempts(quiz_id,submitted_at);
CREATE TABLE quiz_answers (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_id BIGINT NOT NULL, question_id BIGINT NOT NULL,
 question_version_id BIGINT NOT NULL, order_index INT NOT NULL, points DECIMAL(10,2) NOT NULL,
 answer_json TEXT, is_correct BOOLEAN, score DECIMAL(10,2), answered_at TIMESTAMP(6),
 UNIQUE(attempt_id,question_id), FOREIGN KEY(attempt_id) REFERENCES quiz_attempts(id),
 FOREIGN KEY(question_id) REFERENCES questions(id), FOREIGN KEY(question_version_id) REFERENCES question_versions(id)
);
CREATE TABLE learning_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_key VARCHAR(200) NOT NULL UNIQUE,
 event_type VARCHAR(40) NOT NULL, user_id BIGINT NOT NULL, entity_id BIGINT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL, delivered_at TIMESTAMP(6)
);
