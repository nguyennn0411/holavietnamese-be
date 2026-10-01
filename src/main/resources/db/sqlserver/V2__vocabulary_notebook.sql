CREATE TABLE vocabulary_entries (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, user_id BIGINT NOT NULL, lesson_id BIGINT,
 word NVARCHAR(200) NOT NULL, meaning NVARCHAR(1000) NOT NULL, pronunciation NVARCHAR(200),
 example_sentence NVARCHAR(2000), note NVARCHAR(2000), created_at DATETIME2(6) NOT NULL,
 CONSTRAINT fk_vocabulary_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_vocabulary_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);
CREATE INDEX idx_vocabulary_user_created ON vocabulary_entries(user_id, created_at);
