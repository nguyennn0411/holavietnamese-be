# Archived migration collisions

This directory is outside Flyway's default `classpath:db/migration` location.
The original SQL files are preserved here without changes for reference.

- `V13__align_ai_schema.sql` duplicates the schema changes already covered by the
  canonical `V17__align_ai_schema.sql`.
- `V14__add_hearts_to_users.sql` is active as `V21__add_hearts_to_users.sql`.
- `V15__add_api_key_to_ai_settings.sql` is active as
  `V22__add_api_key_to_ai_settings.sql`, after `ai_settings` is created in V16.

The canonical V13, V14 and V15 migrations remain unchanged. Their versions and
checksums, and the V21/V22 assignments, match the existing local migration history.
Do not copy the archived files back into `db/migration`: their version numbers
collide with the canonical migrations.
