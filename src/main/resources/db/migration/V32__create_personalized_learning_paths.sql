CREATE TABLE learning_path_preferences (
 user_id BIGINT PRIMARY KEY,
 confirmed_level VARCHAR(10),
 placement_level VARCHAR(10),
 placement_taken_at TIMESTAMP(6),
 onboarding_level VARCHAR(10),
 level_source VARCHAR(30),
 learning_goals TEXT,
 daily_study_minutes INT,
 active_course_id BIGINT,
 preference_version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_learning_path_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_learning_path_course FOREIGN KEY(active_course_id) REFERENCES courses(id)
);

CREATE INDEX idx_learning_path_active_course ON learning_path_preferences(active_course_id);
