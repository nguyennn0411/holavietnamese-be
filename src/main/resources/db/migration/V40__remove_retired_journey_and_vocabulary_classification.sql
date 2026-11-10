-- Retired sequential Journey tables. Explore is now read-only destination
-- discovery and no longer stores journey order, progress, passport stamps, or
-- destination-to-lesson links.
DROP TABLE IF EXISTS user_passport_entries;
DROP TABLE IF EXISTS user_journey_progress;
DROP TABLE IF EXISTS journey_stops;
DROP TABLE IF EXISTS destination_lessons;

-- Vocabulary no longer classifies master entries by topic. Keep the master
-- catalogue, meanings, examples, notebook, review, regional, lesson, and
-- destination link tables because current code still uses them.
DROP TABLE IF EXISTS vocabulary_topic_mappings;
DROP TABLE IF EXISTS vocabulary_topics;
