# Archived migration collisions

This directory is outside Flyway's default `classpath:db/migration` location.
The original SQL files are preserved here without changes for reference.

- `V13__align_ai_schema.sql` duplicates the schema changes covered by the
  canonical `V14__align_ai_schema.sql` on the shared `backup` branch.
- `V14__add_hearts_to_users.sql` is active as `V19__add_hearts_to_users.sql`.
- `V15__add_api_key_to_ai_settings.sql` is active as
  `V21__add_api_key_to_ai_settings.sql`, after `ai_settings` is created in V13.

The active migration files match the shared `backup` branch. Some local databases
used an earlier numbering scheme; compare their applied history with
`docs/INTEGRATION_DEMO.md` before starting the merged application. This merge does not
rewrite database history or run Flyway Repair automatically.
Do not copy the archived files back into `db/migration`: their version numbers
collide with the canonical migrations.
