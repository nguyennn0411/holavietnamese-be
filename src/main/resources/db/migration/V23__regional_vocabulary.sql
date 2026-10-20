CREATE TABLE regional_vocabularies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vocabulary_id BIGINT NOT NULL,
    region VARCHAR(30) NOT NULL,
    word VARCHAR(200) NOT NULL,
    meaning VARCHAR(1000) NOT NULL,
    pronunciation VARCHAR(200),
    audio_url VARCHAR(2048),
    example_sentence VARCHAR(2000),
    usage_note VARCHAR(2000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_regional_vocabulary UNIQUE (vocabulary_id, region, word),
    CONSTRAINT fk_regional_vocabulary_master FOREIGN KEY (vocabulary_id) REFERENCES vocabularies(id)
);
CREATE INDEX idx_regional_vocabulary_visibility ON regional_vocabularies(status, region);
