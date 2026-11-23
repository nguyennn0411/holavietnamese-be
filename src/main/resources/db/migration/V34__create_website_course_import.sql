CREATE TABLE website_import_jobs (
    id VARCHAR(36) PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source_url VARCHAR(2048),
    source_filename VARCHAR(255),
    source_fingerprint VARCHAR(64) NOT NULL,
    target_course_id BIGINT,
    result_course_id BIGINT,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(500),
    license VARCHAR(500),
    attribution TEXT,
    rights_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    payload LONGTEXT NOT NULL,
    diagnostics LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    CONSTRAINT fk_website_import_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_website_import_target_course FOREIGN KEY (target_course_id) REFERENCES courses(id),
    CONSTRAINT fk_website_import_result_course FOREIGN KEY (result_course_id) REFERENCES courses(id)
);
CREATE INDEX idx_website_import_created ON website_import_jobs(created_at, id);
CREATE INDEX idx_website_import_fingerprint ON website_import_jobs(source_fingerprint, actor_id);

CREATE TABLE website_import_provenance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    import_job_id VARCHAR(36) NOT NULL,
    lesson_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    block_key VARCHAR(80) NOT NULL,
    source_page_url VARCHAR(2048),
    original_url VARCHAR(2048),
    attribution TEXT,
    license VARCHAR(500),
    import_status VARCHAR(40) NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    CONSTRAINT fk_website_provenance_job FOREIGN KEY (import_job_id) REFERENCES website_import_jobs(id),
    CONSTRAINT fk_website_provenance_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id),
    CONSTRAINT fk_website_provenance_activity FOREIGN KEY (activity_id) REFERENCES lesson_activities(id),
    CONSTRAINT uk_website_provenance_source UNIQUE (source_fingerprint, block_key)
);
CREATE INDEX idx_website_provenance_lesson ON website_import_provenance(lesson_id, activity_id);
