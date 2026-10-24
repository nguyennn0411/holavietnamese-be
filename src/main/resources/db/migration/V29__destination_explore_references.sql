-- Explore owns references only. Source content and historical Journey data are preserved.
CREATE TABLE destination_explore_references (
    destination_id BIGINT NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(200) NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (destination_id, content_type, target_id),
    CONSTRAINT fk_explore_reference_destination FOREIGN KEY (destination_id)
        REFERENCES destinations(id) ON DELETE CASCADE
);
