CREATE TABLE learning_path_courses (
 user_id BIGINT NOT NULL,
 course_id BIGINT NOT NULL,
 position INT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id, course_id),
 CONSTRAINT uq_learning_path_course_position UNIQUE(user_id, position),
 CONSTRAINT fk_learning_path_courses_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_learning_path_courses_course FOREIGN KEY(course_id) REFERENCES courses(id)
);

CREATE INDEX idx_learning_path_courses_order ON learning_path_courses(user_id, position);

INSERT INTO learning_path_courses(user_id, course_id, position)
SELECT user_id, active_course_id, 1
FROM learning_path_preferences
WHERE active_course_id IS NOT NULL;
