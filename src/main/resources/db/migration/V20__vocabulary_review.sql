
-- Vocabulary Review / Spaced Repetition
-- Review progress is stored separately from learner notebook entries.

CREATE TABLE vocabulary_review_states (
                                          vocabulary_entry_id BIGINT NOT NULL PRIMARY KEY,

                                          repetition_count INT NOT NULL DEFAULT 0,
                                          interval_minutes BIGINT NOT NULL DEFAULT 0,
                                          ease_factor DECIMAL(4,2) NOT NULL DEFAULT 2.50,
                                          lapse_count INT NOT NULL DEFAULT 0,

                                          last_reviewed_at TIMESTAMP(6) NULL,
                                          next_review_at TIMESTAMP(6) NOT NULL,

                                          updated_at TIMESTAMP(6) NOT NULL
                                              DEFAULT CURRENT_TIMESTAMP(6)
                                              ON UPDATE CURRENT_TIMESTAMP(6),

                                          CONSTRAINT fk_vocabulary_review_state_entry
                                              FOREIGN KEY (vocabulary_entry_id)
                                                  REFERENCES vocabulary_entries(id)
                                                  ON DELETE CASCADE
);

CREATE INDEX idx_vocabulary_review_states_due
    ON vocabulary_review_states(next_review_at);


-- Store each learner review result.

CREATE TABLE vocabulary_review_logs (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                        vocabulary_entry_id BIGINT NOT NULL,

                                        session_id VARCHAR(36) NOT NULL,

                                        rating VARCHAR(10) NOT NULL,
                                        reviewed_at TIMESTAMP(6) NOT NULL,
                                        next_review_at TIMESTAMP(6) NOT NULL,
                                        interval_minutes BIGINT NOT NULL,

                                        CONSTRAINT uk_vocabulary_review_session_entry
                                            UNIQUE (session_id, vocabulary_entry_id),

                                        CONSTRAINT fk_vocabulary_review_log_entry
                                            FOREIGN KEY (vocabulary_entry_id)
                                                REFERENCES vocabulary_entries(id)
                                                ON DELETE CASCADE
);

CREATE INDEX idx_vocabulary_review_logs_entry_time
    ON vocabulary_review_logs(vocabulary_entry_id, reviewed_at);
