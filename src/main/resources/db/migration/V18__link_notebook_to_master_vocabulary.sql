
-- Link learner notebook entries to Master Vocabulary.
-- Existing notebook entries remain unchanged.

ALTER TABLE vocabulary_entries
    ADD COLUMN master_vocabulary_id BIGINT NULL;

ALTER TABLE vocabulary_entries
    ADD CONSTRAINT fk_vocabulary_entry_master
        FOREIGN KEY (master_vocabulary_id)
            REFERENCES vocabularies(id);

CREATE UNIQUE INDEX uk_vocabulary_entry_user_master
    ON vocabulary_entries (user_id, master_vocabulary_id);
