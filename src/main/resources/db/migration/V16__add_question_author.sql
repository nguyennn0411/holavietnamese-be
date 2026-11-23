ALTER TABLE questions ADD created_by BIGINT NULL;
CREATE INDEX idx_questions_author ON questions(created_by);
ALTER TABLE questions ADD CONSTRAINT fk_questions_created_by
  FOREIGN KEY (created_by) REFERENCES users(id);
