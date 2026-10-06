CREATE TABLE IF NOT EXISTS users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS courses (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200) NOT NULL, description TEXT NOT NULL,
 thumbnail_url VARCHAR(2048), level VARCHAR(30) NOT NULL, estimated_duration INT NOT NULL,
 status VARCHAR(20) NOT NULL
);
CREATE TABLE IF NOT EXISTS lessons (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, course_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
 description TEXT, lesson_order INT NOT NULL, content TEXT NOT NULL,
 video_url VARCHAR(2048), audio_url VARCHAR(2048), estimated_duration INT NOT NULL, published BOOLEAN NOT NULL,
 CONSTRAINT fk_lesson_course FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT uk_lesson_order UNIQUE (course_id, lesson_order)
);
CREATE TABLE IF NOT EXISTS enrollments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, course_id BIGINT NOT NULL,
 enrolled_at TIMESTAMP(6) NOT NULL, status VARCHAR(20) NOT NULL,
 last_accessed_lesson_id BIGINT, last_accessed_at TIMESTAMP(6),
 CONSTRAINT uk_enrollment_user_course UNIQUE (user_id, course_id),
 CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT fk_enrollment_last_lesson FOREIGN KEY (last_accessed_lesson_id) REFERENCES lessons(id)
);
CREATE TABLE IF NOT EXISTS lesson_progress (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, enrollment_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, started_at TIMESTAMP(6), completed_at TIMESTAMP(6),
 CONSTRAINT uk_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id),
 CONSTRAINT fk_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id),
 CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);
