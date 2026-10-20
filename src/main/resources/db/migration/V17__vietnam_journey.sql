-- =========================================================
-- Hola Vietnamese
-- Module 3 - Vietnam Journey
-- =========================================================


-- =========================================================
-- 1. DESTINATIONS
-- =========================================================

CREATE TABLE destinations (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,

                              name_vi VARCHAR(150) NOT NULL,
                              name_en VARCHAR(150) NOT NULL,

                              slug VARCHAR(180) NOT NULL,

                              region VARCHAR(30) NOT NULL,

                              short_description_vi VARCHAR(500),
                              short_description_en VARCHAR(500),

                              description_vi TEXT,
                              description_en TEXT,

                              image_url VARCHAR(2048),

                              status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                              published_at TIMESTAMP(6) NULL,
                              archived_at TIMESTAMP(6) NULL,

                              created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                              updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6),

                              CONSTRAINT uk_destinations_slug
                                  UNIQUE (slug)
);

CREATE INDEX idx_destinations_status
    ON destinations(status);

CREATE INDEX idx_destinations_region
    ON destinations(region);

CREATE INDEX idx_destinations_filter
    ON destinations(status, region);


-- =========================================================
-- 2. VIETNAM JOURNEY STOPS
-- Defines destination order in the learner journey
-- =========================================================

CREATE TABLE journey_stops (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,

                               destination_id BIGINT NOT NULL,

                               display_order INT NOT NULL DEFAULT 0,

                               status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                               created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                               updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),

                               CONSTRAINT uk_journey_stops_destination
                                   UNIQUE (destination_id),

                               CONSTRAINT fk_journey_stops_destination
                                   FOREIGN KEY (destination_id)
                                       REFERENCES destinations(id)
                                       ON DELETE CASCADE
);

CREATE INDEX idx_journey_stops_order
    ON journey_stops(display_order);

CREATE INDEX idx_journey_stops_status
    ON journey_stops(status);


-- =========================================================
-- 3. USER JOURNEY PROGRESS
-- =========================================================

CREATE TABLE user_journey_progress (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                       user_id BIGINT NOT NULL,
                                       destination_id BIGINT NOT NULL,

                                       status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',

                                       progress_percent INT NOT NULL DEFAULT 0,

                                       started_at TIMESTAMP(6) NULL,
                                       completed_at TIMESTAMP(6) NULL,
                                       last_accessed_at TIMESTAMP(6) NULL,

                                       created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                       updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),

                                       CONSTRAINT uk_user_journey_progress
                                           UNIQUE (user_id, destination_id),

                                       CONSTRAINT fk_user_journey_progress_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE,

                                       CONSTRAINT fk_user_journey_progress_destination
                                           FOREIGN KEY (destination_id)
                                               REFERENCES destinations(id)
                                               ON DELETE CASCADE
);

CREATE INDEX idx_user_journey_progress_user
    ON user_journey_progress(user_id);

CREATE INDEX idx_user_journey_progress_destination
    ON user_journey_progress(destination_id);

CREATE INDEX idx_user_journey_progress_status
    ON user_journey_progress(status);


-- =========================================================
-- 4. USER PASSPORT ENTRIES
-- A completed destination can grant one passport stamp
-- =========================================================

CREATE TABLE user_passport_entries (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                       user_id BIGINT NOT NULL,
                                       destination_id BIGINT NOT NULL,

                                       stamped_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                       created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                       updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),

                                       CONSTRAINT uk_user_passport_entry
                                           UNIQUE (user_id, destination_id),

                                       CONSTRAINT fk_user_passport_entries_user
                                           FOREIGN KEY (user_id)
                                               REFERENCES users(id)
                                               ON DELETE CASCADE,

                                       CONSTRAINT fk_user_passport_entries_destination
                                           FOREIGN KEY (destination_id)
                                               REFERENCES destinations(id)
                                               ON DELETE CASCADE
);

CREATE INDEX idx_user_passport_entries_user
    ON user_passport_entries(user_id);

CREATE INDEX idx_user_passport_entries_destination
    ON user_passport_entries(destination_id);