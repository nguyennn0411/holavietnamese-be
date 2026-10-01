# Course Excel Import

Backend-only feature. Uses Apache POI 5.5.1, Spring Security session authentication,
and the existing transaction manager. No frontend import screen or learner-facing
content-block API is included.

## Endpoints

Both endpoints require an authenticated **ADMIN** and the current session's CSRF token.

| Endpoint | Multipart fields | Result |
| --- | --- | --- |
| `POST /api/admin/courses/import/validate` | `file`; optional `importMode` (default `CREATE_ONLY`) | HTTP 200 preview, with `success`, statistics, warnings and errors. No database writes. |
| `POST /api/admin/courses/import` | `file`, required `importMode` | HTTP 200 on success; HTTP 400 with the same preview shape if validation fails. |

Modes:

- `CREATE_ONLY`: reject an existing course code.
- `UPDATE_EXISTING`: upsert by stable keys, including creating the course if its code is new.
- Import always reparses and revalidates the upload. A successful preview is not a reservation.
- Existing records cannot be moved to a different parent by reusing their code.
- Rows absent from an update are **never deleted**.
- Every provided row replaces the corresponding row's fields; a blank optional field clears that field.
- Concurrent updates to the same existing course are serialized by a database lock.
  Unique database keys protect concurrent creates. A concurrent conflict can return HTTP 409;
  retry validation before retrying import.
- All writes, including ordering changes, run in one transaction. Fatal persistence errors roll back
  the whole import. Unexpected server failures are not converted to successful previews.

Example result:

```json
{
  "courseCode": "VI-A1",
  "mode": "CREATE_ONLY",
  "success": true,
  "statistics": {
    "courses": 1, "units": 1, "lessons": 1, "blocks": 1,
    "vocabulary": 1, "dialogues": 1, "dialogueLines": 1,
    "drills": 1, "exercises": 1, "options": 2
  },
  "warnings": [],
  "errors": []
}
```

Errors use Excel's **1-based physical row numbers**; headers are row 1.
File-level errors use row 0 and column `file`.

```json
{
  "sheet": "03_LESSONS",
  "row": 12,
  "column": "unit_code",
  "value": "A1-U99",
  "message": "Referenced unit_code does not exist in 02_UNITS."
}
```

Other status codes: 401 unauthenticated, 403 non-admin or invalid CSRF, 400 missing/invalid
parameters, 413 multipart upload too large, 409 database uniqueness/integrity conflict.

## Workbook contract

All ten sheets and **all listed header columns are required**, with exact names.
Headers may be rearranged; unknown columns/sheets are ignored with warnings.
Blank data rows are ignored. Empty content sheets are allowed; `01_COURSE` must have exactly one row.
References must resolve to rows in the **same workbook**, not just records already in the database.

```text
01_COURSE
code,level,title_en,title_vi,description_en,description_vi,estimated_hours,status

02_UNITS
course_code,unit_code,title_en,title_vi,description_en,description_vi,sort_order

03_LESSONS
unit_code,lesson_code,title_en,title_vi,description_en,description_vi,estimated_minutes,sort_order,status

04_BLOCKS
lesson_code,block_code,type,title_en,title_vi,content_en,content_vi,sort_order

05_VOCABULARY
lesson_code,word,meaning_en,meaning_vi,part_of_speech,example_vi,example_en,pronunciation,audio_url,dialect,difficulty

06_DIALOGUES
dialogue_code,lesson_code,title_en,title_vi,situation_en,situation_vi

07_DIALOGUE_LINES
dialogue_code,speaker,text_vi,text_en,sort_order,audio_url

08_DRILLS
lesson_code,vi_text,en_text,grammar_focus,difficulty,sort_order,audio_url

09_EXERCISES
exercise_code,lesson_code,type,question_vi,question_en,correct_answer,explanation_vi,explanation_en,difficulty,sort_order

10_OPTIONS
exercise_code,option_code,text_vi,text_en,is_correct,sort_order
```

Required values (all other values may be blank, but their header columns must exist):

| Sheet | Required non-blank values |
| --- | --- |
| 01_COURSE | code, level, title_en, title_vi, estimated_hours, status |
| 02_UNITS | course_code, unit_code, title_en, title_vi, sort_order |
| 03_LESSONS | unit_code, lesson_code, title_en, title_vi, estimated_minutes, sort_order, status |
| 04_BLOCKS | lesson_code, block_code, type, title_en, title_vi, sort_order |
| 05_VOCABULARY | lesson_code, word, meaning_en, meaning_vi |
| 06_DIALOGUES | dialogue_code, lesson_code, title_en, title_vi |
| 07_DIALOGUE_LINES | dialogue_code, speaker, text_vi, text_en, sort_order |
| 08_DRILLS | lesson_code, vi_text, en_text, sort_order |
| 09_EXERCISES | exercise_code, lesson_code, type, question_vi, question_en, sort_order; correct_answer for objective types |
| 10_OPTIONS | exercise_code, option_code, text_vi, text_en, is_correct, sort_order |

Additional rules:

- Levels: `STARTER, A1, A2, B1, B2, C1, C2`.
- Course and lesson publication status: `DRAFT, PUBLISHED, ARCHIVED`.
  Lesson publication is distinct from learner progress status (`NOT_STARTED/IN_PROGRESS/COMPLETED`).
- Difficulty, when provided: **`EASY, MEDIUM, HARD`**, as confirmed for this project.
- Block types: `INTRO, DIALOGUE, VOCABULARY, PRONUNCIATION, GRAMMAR, LISTENING, SPEAKING,
  READING, WRITING, CULTURE, SENTENCE_DRILL, EXERCISE, QUIZ`.
- Exercise types: `MULTIPLE_CHOICE, TRUE_FALSE, FILL_IN_BLANK, MATCHING,
  ORDER_SENTENCE, LISTENING_CHOICE, SHORT_ANSWER`.
- Every type except `SHORT_ANSWER` needs a non-blank `correct_answer`.
  `TRUE_FALSE` answers are lowercase `true` or `false`.
- Choice exercises (`MULTIPLE_CHOICE` and `LISTENING_CHOICE`) must include options,
  exactly one with `is_correct=true`. The `correct_answer` must equal that option's `option_code`.
  Other objective answers are stored as non-empty text; this importer does not define their grading format.
- `is_correct` accepts an Excel boolean or text `true/false` (case-insensitive).
- Orders and minutes are non-negative integers up to 1,000,000. Sort orders are unique per
  sheet/parent, including retained records on update. Include both rows when swapping their orders.
- Hours may be fractional, but must resolve to a whole number of minutes; e.g. `2.5` becomes 150 minutes.
- Codes: 1–80 ASCII letters/digits/dot/underscore/hyphen, starting with a letter or digit.
  Case-only duplicates are rejected. References use exact spelling.
- Titles, word, speaker and pronunciation: max 200 characters; audio URLs: 2,048;
  enum/label fields: 40; other text: 10,000. Text supports Vietnamese Unicode.
- Audio URLs may be blank. URLs are stored, never fetched by the importer.
- Leading/trailing whitespace is trimmed. Formulas and Excel error cells are rejected;
  paste their values instead. Format codes as text to preserve leading zeroes.
- Upload: non-empty `.xlsx`, max 10 MiB compressed, 50 MiB expanded, 2,000 ZIP entries,
  20,000 rows per sheet, 50,000 total data rows. Encrypted/invalid archives are rejected.
  Apache POI's built-in ZIP-bomb checks remain enabled.

## Stable identity and existing learner data

Global stable keys: course.code, unit_code, lesson_code, block_code, dialogue_code, exercise_code.
For sheets without a globally stable code, these natural keys make repeated updates idempotent:

| Content | Key |
| --- | --- |
| Course vocabulary | lesson_code + word |
| Dialogue line | dialogue_code + sort_order |
| Sentence drill | lesson_code + sort_order |
| Exercise option | exercise_code + option_code |

Changing a natural key creates a new row and retains the old row. In particular, renaming a word
does not delete its previous entry. Vocabulary permits one entry per word per lesson.
Do not reuse a dialogue-line/drill order for a different identity unintentionally.
Database collation may consider case/accent variants equal; use exact existing spellings.

When changing the correct option, include the old correct option with `is_correct=false`;
otherwise the retained option would leave two correct answers and validation rejects the update.

Existing `courses` and `lessons` tables/entities are reused. Their IDs, enrollment links,
lesson progress, media and existing legacy lesson content remain intact.
Imported English title/description populate the existing fields; Vietnamese text has dedicated columns.
Lesson order is recalculated from stored unit order and lesson-within-unit order, retaining
omitted lessons. Legacy lessons without units follow imported lessons in their previous relative order.
New block content is stored in `lesson_blocks`, not concatenated into legacy `lessons.content`.

New curriculum tables: `course_units, lesson_blocks, course_vocabulary, dialogues, dialogue_lines,
sentence_drills, exercises, exercise_options`. Course vocabulary is intentionally separate from
the existing personal `vocabulary_entries` notebook.

## Migration and ADMIN access

Flyway V3 is additive for both MySQL and SQL Server. Do not edit applied V1/V2 migrations.
Existing courses/lessons can keep null codes; SQL Server uses filtered unique indexes for this.
Existing users receive role `LEARNER`; **no account is automatically elevated**.

A database administrator can deliberately grant the role to an existing trusted account:

```sql
-- Replace with the exact trusted account; inspect it before updating.
SELECT id, email, role FROM users WHERE email = 'your-admin@example.com';
UPDATE users SET role = 'ADMIN' WHERE email = 'your-admin@example.com';
```

Sign out and sign in again after a role change. Session authorities are loaded at login.
ADMIN also retains LEARNER authority for existing learner APIs. Do not expose a public role-update API.

For local SQL Server startup, see [SQLSERVER_DOCKER.md](SQLSERVER_DOCKER.md).
Rebuild and restart the backend to apply V3 and load the new endpoints.

## Try it with PowerShell 7

Use an existing account explicitly granted ADMIN. Credentials are prompted and not embedded in source:

```powershell
$base = 'http://localhost:8080'
$credential = Get-Credential
$csrf = Invoke-RestMethod "$base/api/csrf" -SessionVariable holaSession
Invoke-WebRequest "$base/api/login" -Method Post -WebSession $holaSession `
  -Headers @{ $csrf.headerName = $csrf.token } `
  -Body @{ username = $credential.UserName; password = $credential.GetNetworkCredential().Password }

# Login rotates the CSRF token; retrieve the current token.
$csrf = Invoke-RestMethod "$base/api/csrf" -WebSession $holaSession
$preview = Invoke-RestMethod "$base/api/admin/courses/import/validate" -Method Post `
  -WebSession $holaSession -Headers @{ $csrf.headerName = $csrf.token } `
  -Form @{ file = Get-Item '.\course.xlsx'; importMode = 'CREATE_ONLY' }
$preview | ConvertTo-Json -Depth 8

if ($preview.success) {
  Invoke-RestMethod "$base/api/admin/courses/import" -Method Post `
    -WebSession $holaSession -Headers @{ $csrf.headerName = $csrf.token } `
    -Form @{ file = Get-Item '.\course.xlsx'; importMode = 'CREATE_ONLY' }
}
```

## Implementation and tests

- REST endpoint: `controller/CourseImportController` calls `service/CourseImportService` directly.
- Workbook parsing and validation: `service/importing/ExcelCourseParser` and `CourseImportValidator`.
- Workbook contract and data: `dto/courseimport`; rejection exception: `exception/CourseImportRejected`.
- No use-case or repository port interfaces are required in the layered monolith.
- Persistence: `CourseImportRepository` reuses JPA Course/Lesson entities and uses
  parameterized JDBC for the new curriculum tables within the same Spring JPA transaction.
  All SQL identifiers come from fixed repository mappings, never from spreadsheet headers.
- No POI or JDBC code is added to JPA entities or DTO models.

Run all tests with Java 21:

```powershell
& 'C:\apache-maven-3.9.12\bin\mvn.cmd' clean test
```

The default test profile uses isolated H2. SQL Server tests require an existing dedicated
`hola_features_test` database in the local Docker instance:

```powershell
$env:MSSQL_SA_PASSWORD = (Get-Content '.env.sqlserver' |
  Where-Object { $_ -like 'MSSQL_SA_PASSWORD=*' }).Substring(18)
$env:SPRING_PROFILES_ACTIVE = 'sqlserver'
$env:SQLSERVER_URL = 'jdbc:sqlserver://localhost:14330;databaseName=hola_features_test;encrypt=true;trustServerCertificate=true'
try {
  & 'C:\apache-maven-3.9.12\bin\mvn.cmd' '-Dmaven.repo.local=.m2' test
} finally {
  Remove-Item Env:MSSQL_SA_PASSWORD, Env:SPRING_PROFILES_ACTIVE, Env:SQLSERVER_URL -ErrorAction SilentlyContinue
}
```

Integration tests clear fixture tables only after verifying the dedicated test database.
**Never point test commands at the development/production database.**
Tests cover valid workbooks, every persisted sheet, read-only previews, sheet/column errors,
invalid references/enums/numbers, duplicate keys, malformed/formula files, option rules,
create conflicts, idempotent update, omitted records, preserved progress/IDs,
transaction rollback for create/update, Vietnamese Unicode, ADMIN login/authorization,
CSRF, retained order conflicts, and concurrent creates.
