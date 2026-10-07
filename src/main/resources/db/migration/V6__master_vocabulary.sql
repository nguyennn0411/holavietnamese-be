-- =========================================================
-- Hola Vietnamese
-- Module 3 - Master Vocabulary Catalog
-- =========================================================


-- =========================================================
-- 1. VOCABULARY TOPICS
-- =========================================================

CREATE TABLE vocabulary_topics (
                                   id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                   name VARCHAR(100) NOT NULL,
                                   slug VARCHAR(120) NOT NULL,
                                   description VARCHAR(500),

                                   display_order INT NOT NULL DEFAULT 0,

                                   status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                                   created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),

                                   CONSTRAINT uk_vocabulary_topics_slug
                                       UNIQUE (slug)
);

CREATE INDEX idx_vocabulary_topics_status
    ON vocabulary_topics(status);

CREATE INDEX idx_vocabulary_topics_display_order
    ON vocabulary_topics(display_order);


-- =========================================================
-- 2. MASTER VOCABULARIES
-- =========================================================

CREATE TABLE vocabularies (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,

                              word VARCHAR(200) NOT NULL,

                              pronunciation VARCHAR(200),

                              part_of_speech VARCHAR(40) NOT NULL,

                              cefr_level VARCHAR(10) NOT NULL,

                              audio_url VARCHAR(2048),

                              status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                              published_at TIMESTAMP(6) NULL,
                              archived_at TIMESTAMP(6) NULL,

                              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_vocabularies_word
    ON vocabularies(word);

CREATE INDEX idx_vocabularies_status
    ON vocabularies(status);

CREATE INDEX idx_vocabularies_level
    ON vocabularies(cefr_level);

CREATE INDEX idx_vocabularies_part_of_speech
    ON vocabularies(part_of_speech);

CREATE INDEX idx_vocabularies_filter
    ON vocabularies(status, cefr_level, part_of_speech);


-- =========================================================
-- 3. VOCABULARY MEANINGS
-- =========================================================

CREATE TABLE vocabulary_meanings (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                     vocabulary_id BIGINT NOT NULL,

                                     translation_en VARCHAR(500) NOT NULL,

                                     definition_en TEXT,

                                     usage_note TEXT,

                                     display_order INT NOT NULL DEFAULT 0,

                                     created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),

                                     CONSTRAINT fk_vocabulary_meanings_vocabulary
                                         FOREIGN KEY (vocabulary_id)
                                             REFERENCES vocabularies(id)
                                             ON DELETE CASCADE
);

CREATE INDEX idx_vocabulary_meanings_vocabulary
    ON vocabulary_meanings(vocabulary_id);

CREATE INDEX idx_vocabulary_meanings_translation
    ON vocabulary_meanings(translation_en);


-- =========================================================
-- 4. VOCABULARY EXAMPLES
-- =========================================================

CREATE TABLE vocabulary_examples (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                     vocabulary_meaning_id BIGINT NOT NULL,

                                     example_vi TEXT NOT NULL,

                                     translation_en TEXT NOT NULL,

                                     audio_url VARCHAR(2048),

                                     display_order INT NOT NULL DEFAULT 0,

                                     created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),

                                     CONSTRAINT fk_vocabulary_examples_meaning
                                         FOREIGN KEY (vocabulary_meaning_id)
                                             REFERENCES vocabulary_meanings(id)
                                             ON DELETE CASCADE
);

CREATE INDEX idx_vocabulary_examples_meaning
    ON vocabulary_examples(vocabulary_meaning_id);


-- =========================================================
-- 5. VOCABULARY <-> TOPIC
-- =========================================================

CREATE TABLE vocabulary_topic_mappings (
                                           vocabulary_id BIGINT NOT NULL,
                                           topic_id BIGINT NOT NULL,

                                           created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                           PRIMARY KEY (vocabulary_id, topic_id),

                                           CONSTRAINT fk_vocabulary_topic_mapping_vocabulary
                                               FOREIGN KEY (vocabulary_id)
                                                   REFERENCES vocabularies(id)
                                                   ON DELETE CASCADE,

                                           CONSTRAINT fk_vocabulary_topic_mapping_topic
                                               FOREIGN KEY (topic_id)
                                                   REFERENCES vocabulary_topics(id)
                                                   ON DELETE CASCADE
);

CREATE INDEX idx_vocabulary_topic_mapping_topic
    ON vocabulary_topic_mappings(topic_id);