CREATE TABLE course_edit_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT NULL,
    review_note VARCHAR(2000) NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_edit_request_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_edit_request_requester FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_course_edit_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT ck_course_edit_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_course_edit_request_course_status
    ON course_edit_requests(course_id, status, requested_at);

CREATE INDEX idx_course_edit_request_status_time
    ON course_edit_requests(status, requested_at);
