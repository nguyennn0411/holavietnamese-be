CREATE TABLE content_import_locks (
    module VARCHAR(40) PRIMARY KEY
);
-- Configuration only: serialize confirmations for the same content handler.
INSERT INTO content_import_locks(module) VALUES ('VOCABULARY');

CREATE TABLE content_import_jobs (
    id VARCHAR(36) PRIMARY KEY,
    module VARCHAR(40) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_name VARCHAR(254) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_rows INT NOT NULL,
    valid_rows INT NOT NULL,
    error_rows INT NOT NULL,
    imported_rows INT NOT NULL DEFAULT 0,
    payload LONGTEXT NOT NULL,
    diagnostics LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    CONSTRAINT fk_content_import_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_content_import_created ON content_import_jobs(created_at, id);
