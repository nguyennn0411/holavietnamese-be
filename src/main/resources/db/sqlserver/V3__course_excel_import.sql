-- Additive migration: legacy records and learner progress keep their existing IDs.
ALTER TABLE users ADD role NVARCHAR(20) NOT NULL DEFAULT 'LEARNER';
GO
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('LEARNER','ADMIN'));
ALTER TABLE courses ADD code NVARCHAR(80);
ALTER TABLE courses ADD title_vi NVARCHAR(200);
ALTER TABLE courses ADD description_vi NVARCHAR(MAX);
GO
CREATE UNIQUE INDEX uk_courses_code ON courses(code) WHERE code IS NOT NULL;

CREATE TABLE course_units (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 course_id BIGINT NOT NULL,
 code NVARCHAR(80) NOT NULL,
 title_en NVARCHAR(200) NOT NULL,
 title_vi NVARCHAR(200) NOT NULL,
 description_en NVARCHAR(MAX) NOT NULL,
 description_vi NVARCHAR(MAX) NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_course_units_parent FOREIGN KEY (course_id) REFERENCES courses(id),
 CONSTRAINT uk_course_units_key UNIQUE (code)
);
CREATE INDEX idx_course_units_parent ON course_units(course_id);

ALTER TABLE lessons ADD code NVARCHAR(80);
ALTER TABLE lessons ADD title_vi NVARCHAR(200);
ALTER TABLE lessons ADD description_vi NVARCHAR(MAX);
ALTER TABLE lessons ADD unit_id BIGINT;
ALTER TABLE lessons ADD unit_sort_order INT;
ALTER TABLE lessons ADD publication_status NVARCHAR(20);
GO
ALTER TABLE lessons ADD CONSTRAINT fk_lesson_unit FOREIGN KEY (unit_id) REFERENCES course_units(id);
CREATE UNIQUE INDEX uk_lessons_code ON lessons(code) WHERE code IS NOT NULL;
CREATE INDEX idx_lessons_unit ON lessons(unit_id);

CREATE TABLE lesson_blocks (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code NVARCHAR(80) NOT NULL,
 type NVARCHAR(40) NOT NULL,
 title_en NVARCHAR(200) NOT NULL,
 title_vi NVARCHAR(200) NOT NULL,
 content_en NVARCHAR(MAX) NOT NULL,
 content_vi NVARCHAR(MAX) NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_lesson_blocks_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_lesson_blocks_key UNIQUE (code)
);
CREATE INDEX idx_lesson_blocks_parent ON lesson_blocks(lesson_id);

CREATE TABLE course_vocabulary (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 word NVARCHAR(200) NOT NULL,
 meaning_en NVARCHAR(MAX) NOT NULL,
 meaning_vi NVARCHAR(MAX) NOT NULL,
 part_of_speech NVARCHAR(40) NOT NULL,
 example_vi NVARCHAR(MAX) NOT NULL,
 example_en NVARCHAR(MAX) NOT NULL,
 pronunciation NVARCHAR(200) NOT NULL,
 audio_url NVARCHAR(2048) NOT NULL,
 dialect NVARCHAR(40) NOT NULL,
 difficulty NVARCHAR(40) NOT NULL,
 CONSTRAINT fk_course_vocabulary_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_course_vocabulary_key UNIQUE (lesson_id,word)
);
CREATE INDEX idx_course_vocabulary_parent ON course_vocabulary(lesson_id);

CREATE TABLE dialogues (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code NVARCHAR(80) NOT NULL,
 title_en NVARCHAR(200) NOT NULL,
 title_vi NVARCHAR(200) NOT NULL,
 situation_en NVARCHAR(MAX) NOT NULL,
 situation_vi NVARCHAR(MAX) NOT NULL,
 CONSTRAINT fk_dialogues_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_dialogues_key UNIQUE (code)
);
CREATE INDEX idx_dialogues_parent ON dialogues(lesson_id);

CREATE TABLE dialogue_lines (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 dialogue_id BIGINT NOT NULL,
 speaker NVARCHAR(200) NOT NULL,
 text_vi NVARCHAR(MAX) NOT NULL,
 text_en NVARCHAR(MAX) NOT NULL,
 sort_order INT NOT NULL,
 audio_url NVARCHAR(2048) NOT NULL,
 CONSTRAINT fk_dialogue_lines_parent FOREIGN KEY (dialogue_id) REFERENCES dialogues(id),
 CONSTRAINT uk_dialogue_lines_key UNIQUE (dialogue_id,sort_order)
);
CREATE INDEX idx_dialogue_lines_parent ON dialogue_lines(dialogue_id);

CREATE TABLE sentence_drills (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 vi_text NVARCHAR(MAX) NOT NULL,
 en_text NVARCHAR(MAX) NOT NULL,
 grammar_focus NVARCHAR(MAX) NOT NULL,
 difficulty NVARCHAR(40) NOT NULL,
 sort_order INT NOT NULL,
 audio_url NVARCHAR(2048) NOT NULL,
 CONSTRAINT fk_sentence_drills_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_sentence_drills_key UNIQUE (lesson_id,sort_order)
);
CREATE INDEX idx_sentence_drills_parent ON sentence_drills(lesson_id);

CREATE TABLE exercises (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 lesson_id BIGINT NOT NULL,
 code NVARCHAR(80) NOT NULL,
 type NVARCHAR(40) NOT NULL,
 question_vi NVARCHAR(MAX) NOT NULL,
 question_en NVARCHAR(MAX) NOT NULL,
 correct_answer NVARCHAR(MAX) NOT NULL,
 explanation_vi NVARCHAR(MAX) NOT NULL,
 explanation_en NVARCHAR(MAX) NOT NULL,
 difficulty NVARCHAR(40) NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_exercises_parent FOREIGN KEY (lesson_id) REFERENCES lessons(id),
 CONSTRAINT uk_exercises_key UNIQUE (code)
);
CREATE INDEX idx_exercises_parent ON exercises(lesson_id);

CREATE TABLE exercise_options (
 id BIGINT IDENTITY(1,1) PRIMARY KEY,
 exercise_id BIGINT NOT NULL,
 option_code NVARCHAR(80) NOT NULL,
 text_vi NVARCHAR(MAX) NOT NULL,
 text_en NVARCHAR(MAX) NOT NULL,
 is_correct BIT NOT NULL,
 sort_order INT NOT NULL,
 CONSTRAINT fk_exercise_options_parent FOREIGN KEY (exercise_id) REFERENCES exercises(id),
 CONSTRAINT uk_exercise_options_key UNIQUE (exercise_id,option_code)
);
CREATE INDEX idx_exercise_options_parent ON exercise_options(exercise_id);
