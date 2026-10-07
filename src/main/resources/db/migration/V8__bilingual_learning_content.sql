-- Additive bilingual fields. Existing English/legacy columns and immutable attempts remain intact.
ALTER TABLE lessons ADD COLUMN learning_objective_vi TEXT;
ALTER TABLE lesson_activities ADD COLUMN title_vi VARCHAR(200);
ALTER TABLE lesson_activities ADD COLUMN instruction_vi TEXT;
ALTER TABLE grammar_topics ADD COLUMN title_vi VARCHAR(200);
ALTER TABLE grammar_topics ADD COLUMN description_vi TEXT;
ALTER TABLE grammar_topics ADD COLUMN explanation_vi TEXT;
ALTER TABLE grammar_topics ADD COLUMN common_mistakes_vi TEXT;
ALTER TABLE grammar_topics ADD COLUMN structure_pattern_en VARCHAR(1000);
ALTER TABLE quizzes ADD COLUMN title_vi VARCHAR(200);
ALTER TABLE quizzes ADD COLUMN description_vi TEXT;
ALTER TABLE questions ADD COLUMN prompt_vi TEXT;
ALTER TABLE questions ADD COLUMN explanation_vi TEXT;
ALTER TABLE question_versions ADD COLUMN prompt_vi TEXT;
ALTER TABLE question_versions ADD COLUMN explanation_vi TEXT;
ALTER TABLE quiz_attempts ADD COLUMN quiz_title_vi VARCHAR(200);
