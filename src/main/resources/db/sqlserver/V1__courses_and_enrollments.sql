CREATE TABLE users (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, email NVARCHAR(254) NOT NULL UNIQUE,
 password_hash NVARCHAR(100) NOT NULL, enabled BIT NOT NULL DEFAULT 1
);
CREATE TABLE courses (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, title NVARCHAR(200) NOT NULL, description NVARCHAR(MAX) NOT NULL,
 thumbnail_url NVARCHAR(2048), level NVARCHAR(30) NOT NULL, estimated_duration INT NOT NULL,
 status NVARCHAR(20) NOT NULL
);
CREATE TABLE lessons (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, course_id BIGINT NOT NULL, title NVARCHAR(200) NOT NULL,
 description NVARCHAR(MAX), lesson_order INT NOT NULL, content NVARCHAR(MAX) NOT NULL,
 video_url NVARCHAR(2048), audio_url NVARCHAR(2048), estimated_duration INT NOT NULL, published BIT NOT NULL,
 CONSTRAINT fk_lesson_course FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT uk_lesson_order UNIQUE (course_id, lesson_order)
);
CREATE TABLE enrollments (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, user_id BIGINT NOT NULL, course_id BIGINT NOT NULL,
 enrolled_at DATETIME2(6) NOT NULL, status NVARCHAR(20) NOT NULL,
 last_accessed_lesson_id BIGINT, last_accessed_at DATETIME2(6),
 CONSTRAINT uk_enrollment_user_course UNIQUE (user_id, course_id),
 CONSTRAINT fk_enrollment_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT fk_enrollment_last_lesson FOREIGN KEY (last_accessed_lesson_id) REFERENCES lessons(id)
);
CREATE TABLE lesson_progress (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, enrollment_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL,
 status NVARCHAR(20) NOT NULL, started_at DATETIME2(6), completed_at DATETIME2(6),
 CONSTRAINT uk_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id),
 CONSTRAINT fk_progress_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id),
 CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);
