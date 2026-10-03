-- Additive migration: legacy records and learner progress keep their existing IDs.
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'LEARNER';
ALTER TABLE courses ADD COLUMN IF NOT EXISTS code VARCHAR(80);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS title_vi VARCHAR(200);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS description_vi TEXT;

CREATE TABLE IF NOT EXISTS course_units (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 code VARCHAR(80) NOT NULL,
 title_en VARCHAR(200) NOT NULL,
 title_vi VARCHAR(200) NOT NULL,
 description_en TEXT NOT NULL,
 description_vi TEXT NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_course_units_parent FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT uk_course_units_key UNIQUE (code)
);

ALTER TABLE lessons ADD COLUMN IF NOT EXISTS code VARCHAR(80);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS title_vi VARCHAR(200);
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS description_vi TEXT;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS unit_id BIGINT;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS unit_sort_order INT;
ALTER TABLE lessons ADD COLUMN IF NOT EXISTS publication_status VARCHAR(20);

CREATE TABLE IF NOT EXISTS lesson_blocks (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code VARCHAR(80) NOT NULL,
 type VARCHAR(40) NOT NULL,
 title_en VARCHAR(200) NOT NULL,
 title_vi VARCHAR(200) NOT NULL,
 content_en TEXT NOT NULL,
 content_vi TEXT NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_lesson_blocks_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_lesson_blocks_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS course_vocabulary (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 word VARCHAR(200) NOT NULL,
 meaning_en TEXT NOT NULL,
 meaning_vi TEXT NOT NULL,
 part_of_speech VARCHAR(40) NOT NULL,
 example_vi TEXT NOT NULL,
 example_en TEXT NOT NULL,
 pronunciation VARCHAR(200) NOT NULL,
 audio_url VARCHAR(2048) NOT NULL,
 dialect VARCHAR(40) NOT NULL,
 difficulty VARCHAR(40) NOT NULL,
 CONSTRAINT fk_course_vocabulary_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_course_vocabulary_key UNIQUE (lesson_id,word)
);

CREATE TABLE IF NOT EXISTS dialogues (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code VARCHAR(80) NOT NULL,
 title_en VARCHAR(200) NOT NULL,
 title_vi VARCHAR(200) NOT NULL,
 situation_en TEXT NOT NULL,
 situation_vi TEXT NOT NULL,
 CONSTRAINT fk_dialogues_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_dialogues_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS dialogue_lines (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 dialogue_id BIGINT NOT NULL,
 speaker VARCHAR(200) NOT NULL,
 text_vi TEXT NOT NULL,
 text_en TEXT NOT NULL,
 sort_order INT NOT NULL,
 audio_url VARCHAR(2048) NOT NULL,
 CONSTRAINT fk_dialogue_lines_parent FOREIGN KEY (dialogue_id) REFERENCES dialogues(id),
 CONSTRAINT uk_dialogue_lines_key UNIQUE (dialogue_id,sort_order)
);

CREATE TABLE IF NOT EXISTS sentence_drills (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 vi_text TEXT NOT NULL,
 en_text TEXT NOT NULL,
 grammar_focus TEXT NOT NULL,
 difficulty VARCHAR(40) NOT NULL,
 sort_order INT NOT NULL,
 audio_url VARCHAR(2048) NOT NULL,
 CONSTRAINT fk_sentence_drills_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_sentence_drills_key UNIQUE (lesson_id,sort_order)
);

CREATE TABLE IF NOT EXISTS exercises (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code VARCHAR(80) NOT NULL,
 type VARCHAR(40) NOT NULL,
 question_vi TEXT NOT NULL,
 question_en TEXT NOT NULL,
 correct_answer TEXT NOT NULL,
 explanation_vi TEXT NOT NULL,
 explanation_en TEXT NOT NULL,
 difficulty VARCHAR(40) NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_exercises_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_exercises_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS exercise_options (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 exercise_id BIGINT NOT NULL,
 option_code VARCHAR(80) NOT NULL,
 text_vi TEXT NOT NULL,
 text_en TEXT NOT NULL,
 is_correct BOOLEAN NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_exercise_options_parent FOREIGN KEY (exercise_id) REFERENCES exercises(id),
 CONSTRAINT uk_exercise_options_key UNIQUE (exercise_id,option_code)
);
