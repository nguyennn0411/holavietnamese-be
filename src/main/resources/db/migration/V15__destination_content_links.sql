-- Journey owns links only; the source modules retain ownership of their content.
CREATE TABLE destination_lessons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    destination_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT uk_destination_lesson UNIQUE (destination_id, lesson_id),
    CONSTRAINT fk_destination_lesson_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_destination_lesson_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);
CREATE TABLE destination_vocabularies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    destination_id BIGINT NOT NULL,
    vocabulary_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    CONSTRAINT uk_destination_vocabulary UNIQUE (destination_id, vocabulary_id),
    CONSTRAINT fk_destination_vocabulary_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE CASCADE,
    CONSTRAINT fk_destination_vocabulary_word FOREIGN KEY (vocabulary_id) REFERENCES vocabularies(id) ON DELETE CASCADE
);
