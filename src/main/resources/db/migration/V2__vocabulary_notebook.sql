CREATE TABLE IF NOT EXISTS vocabulary_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, lesson_id BIGINT,
 word VARCHAR(200) NOT NULL, meaning VARCHAR(1000) NOT NULL, pronunciation VARCHAR(200),
 example_sentence VARCHAR(2000), note VARCHAR(2000), created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_vocabulary_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_vocabulary_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);
CREATE INDEX idx_vocabulary_user_created ON vocabulary_entries(user_id, created_at);
