# Vietnamese Foundations A0–A1

Course code: `VI-A0-A1-FOUNDATION`. A separate published course, preserving all existing courses and learner history.

## Persisted curriculum

- 10 modules, 48 required lessons, 1,940 estimated minutes (32 hours 20 minutes).
- 370 contextual vocabulary entries covering 358 distinct words/phrases. Repeated core vocabulary is deliberately revisited in Family and Daily Activities.
- 339 required activities: introduction, vocabulary with examples, sound pattern or grammar, audio, original contextual examples/reading, scored matching practice, and quiz. Three lessons also require a checkpoint.
- 51 assessments: 47 regular lesson quizzes, the final assessment in lesson 48, and 3 checkpoints (15, 20, 20 questions after lessons 4, 22, 35).
- 263 distinct authored questions; 315 question placements including checkpoint reuse. Formats include multiple choice, matching, fill in the blank, translation, sentence ordering and listening dictation/recognition.
- 104 bundled WAV recordings, generated with the installed Microsoft An Vietnamese voice. The lesson UI identifies these as synthetic speech. They are not human/native-speaker recordings or microphone scoring.

| Module | Lessons | Title |
|---|---|---|
| 1 | 1–4 | Khởi đầu với tiếng Việt / Getting Started with Vietnamese |
| 2 | 5–9 | Nguyên âm và âm cơ bản / Essential Vowels and Sounds |
| 3 | 10–16 | Phụ âm: b đến ngh / Consonants: B through NGH |
| 4 | 17–22 | Phụ âm mở rộng và nguyên âm đôi / More Consonants and Vowel Sequences |
| 5 | 23–28 | Con người và cuộc sống / People and Everyday Life |
| 6 | 29–35 | Vần cơ bản: m/p / Basic Rimes Ending in M/P |
| 7 | 36–38 | Vần m/p: o, ô, ơ / M/P Rimes with O, Ô and Ơ |
| 8 | 39–41 | Vần m/p: u, uô, ươ / M/P Rimes with U, UÔ and ƯƠ |
| 9 | 42–45 | Vần n/t và đời sống / N/T Rimes and Daily Life |
| 10 | 46–48 | Vần mở rộng và tổng ôn A0–A1 / Extended Rimes and Final Review |

The supplied outline named seven modules while requesting ten. The approved split above preserves all 48 lesson numbers and their order. The project uses CEFR values (`A0`, `A1`, …), so the database level is `A1`; the title identifies the A0–A1 beginner span.

## Authoring and provenance

`scripts/author-foundation.cjs` contains the reviewed editorial material and emits `src/main/resources/learning/foundation-course.json`. The Java seed reads that resource once and creates real relational content through the existing authoring services. React retrieves content from the API and contains no curriculum data or answer keys.

The pedagogical sequence follows the user-supplied outline referring to *Tiếng Việt 1, Tập 1 — Cánh Diều*. No source PDF was attached to this task. No book story, reading passage, illustration or exercise was copied. The contextual examples, short readings, dialogues and assessment prompts are newly authored for this course. Familiar isolated words and language rules follow the outline.

Editorial correction: `hết` and `mệt` have the rime `êt`, not `et`. Lesson 45 uses `đen/khen` and `nét/vẹt` for `en/et`, then explicitly contrasts `êt`. Regional pronunciation differences are described rather than marked wrong. Less common `uôm` words are recognition practice rather than a high-frequency vocabulary target.

Regenerate the versioned authoring resource with:

```powershell
node scripts/author-foundation.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/render-foundation-audio.ps1
```

Audio generation needs the installed Vietnamese Microsoft An voice on Windows. The bundled WAV files make playback independent of Windows, TTS services, browser voices or network calls. Production reverse proxies must forward `/media/foundation/**` to the backend, as the Vite development proxy already does.

## Real database seed

Flyway `V9__course_final_assessment.sql` adds the designated final assessment relationship. `FoundationCourseSeed` persists modules, lessons, grammar topics/examples, activities, versioned questions, quiz mappings and the prerequisite chain in one transaction. Checked exceptions roll the transaction back as well. It validates lesson count/order and required audio resources before writing. An existing course with the same code is retained; an incompatible module/lesson count fails explicitly. Running the seed again does not reset progress, regenerate IDs or duplicate the course.

Enable the startup seed using `APP_FOUNDATION_SEED_ENABLED=true` or the provided Compose overlay. An existing ADMIN account supplies authorship. For the existing local demo configuration:

```powershell
docker compose --env-file .env.mysql -f compose.yml -f compose.demo.yml -f compose.foundation.yml up -d --build backend
```

For an environment with its own admin, omit `compose.demo.yml`. The foundation seed itself creates no demo users and requires no fixed user IDs or credentials. After the first successful seed the enable flag may be disabled; all curriculum remains in MySQL. Later editorial revisions to an existing seeded course should use explicit migrations or versioned authoring changes, preserving attempt snapshots; editing the source JSON alone intentionally does not overwrite existing learner content.

## Progress and grading

Practice is checked server-side. A wrong answer stays in progress. Quiz attempts snapshot questions, answer configurations, scores and passing thresholds; learner APIs hide correct answers and explanations until submission. Accent marks remain significant during text grading.

Every published required activity must complete, including a quiz score of at least 70%. Checkpoints are required too. Each lesson depends on the previous one, including across module boundaries. The course completes only after all 48 lessons **and** its designated final assessment pass. A failed attempt preserves completed activities and allows retry.

The final has 20 questions worth 5 points each: listening 20%, sound recognition 15%, vocabulary 20%, reading 20%, grammar 15%, communication 10%. Result bands are Needs Review (0–49), Almost There (50–69), Passed (70–84), Excellent (85–100). Required final quiz activity plus the course-level final relationship both guard completion.

All new learner content, quiz prompts/options/explanations, matching controls and completion messages use Vietnamese above English. Body Vietnamese is 24px/600 and English is 14px/400; navigation captions use a smaller paired hierarchy. Audio captions clearly identify synthetic speech.

## Verification

- Full backend suite on H2 and on isolated MySQL: 76 tests, 0 failures/errors, 1 pre-existing optional workbook test skipped.
- `FoundationCourseIntegrationTests` verifies all 48 lessons, all 51 quizzes, vocabulary/content completeness, real WAV headers, no answer leaks, locked lesson access, wrong practice, failing retry, exact 70% final pass, unfinished required activities, seed idempotence and course completion events.
- `node scripts/verify-foundation.cjs` exercises the real deployed HTTP APIs against MySQL using a new QA learner; it completes the entire course and saves the report in ignored `.verification/foundation-runtime-result.json`.
- `node scripts/verify-foundation.cjs --restart-check` logs in as that QA learner after a backend restart and verifies 48 completed lessons and a persisted COMPLETED course. `--browser-user` creates a separate learner for UI checks.
- Frontend production build succeeds. Its existing large-bundle warning remains.
- Browser QA completed a real lesson: introduction, vocabulary, sound pattern, playable audio, examples, matching practice, quiz score 100%, lesson completion, access to lesson 2, and course progress 1/48. This uses a separate QA learner from the API run that completed 48/48.

Local verification artifacts and QA credentials are stored only in ignored `.verification/`. The seed does not contain test attempts or fabricated learner progress.
