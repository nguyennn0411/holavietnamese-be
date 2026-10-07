-- The designated final is independent of lesson completion records.
ALTER TABLE courses ADD final_assessment_quiz_id BIGINT;
ALTER TABLE courses ADD CONSTRAINT fk_course_final_assessment
  FOREIGN KEY (final_assessment_quiz_id) REFERENCES quizzes(id);
