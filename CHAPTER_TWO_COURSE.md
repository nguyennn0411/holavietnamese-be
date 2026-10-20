# Course 02 — Chào hỏi và giới thiệu bằng tiếng Việt

Course code: `VI-GREET-CH02`. Level: `A1`. Duration: 550 minutes (9 hours 10 minutes). The source is Chapter 2, PDF pages 22–37, of the user-provided *Easy Vietnamese*. The 18 lessons below are newly authored around the chapter's teaching points; the textbook's conversations and exercises are not copied.

| Module | Lessons | Nội dung |
| --- | --- | --- |
| 1 | 1–3 | Chào hỏi và xưng hô |
| 2 | 4–6 | Tên và trật tự câu |
| 3 | 7–9 | Quốc gia và quốc tịch |
| 4 | 10–12 | Nghề nghiệp và nơi sống |
| 5 | 13–15 | Hỏi thăm và sở thích |
| 6 | 16–18 | Giới thiệu người khác và tổng ôn |

The curriculum covers forms of address (anh, chị, em, bác, cô, chú, ông, bà), tên/tên của tôi, ấy for third-person reference, common sentence order and là, người nước nào, a broad list of countries and professions, sống ở đâu, có…không, khỏe/mệt/đói/no, thích/không thích, giới thiệu người khác, and social context around names, greetings and personal questions.

**Content:** 18 bilingual lessons, 16 persisted grammar topics with examples, 90 lesson questions, 50 final questions, 87 bundled Vietnamese WAV recordings and 25 quizzes. Each lesson has a short explanation, vocabulary, a grammar or cultural note, an original dialogue, listening audio, scored matching/listening practice and a quiz. Each module ends with a required checkpoint. The final has 50 questions, 100 points and a passing score of 70%; all quizzes allow retries. A learner must complete the activities and pass the final for course completion. The current application permits lessons to be accessed in any order; no cross-course enrollment prerequisite was added.

The PDF's pronunciation spellings are not copied into the course. Audio uses the locally installed Microsoft An synthetic Vietnamese voice and is labelled as synthetic speech. It is not the textbook's audio or a human recording. Culture notes are phrased as context, including the right to decline personal questions and the fact that greeting habits vary.

## Authoring and database

`scripts/author-chapter-two.cjs` writes `src/main/resources/learning/chapter-two-course.json` and its audio manifest. `scripts/render-chapter-two-audio.ps1` creates the bundled WAV files. `ChapterTwoCourseSeed` persists the content through the existing course, grammar, activity, question and quiz services inside a transaction. The optional `APP_CHAPTERTWO_SEED_ENABLED=true` runner requires an existing ADMIN. Re-running the seed keeps the existing course ID and all learner history; a conflicting structure fails explicitly. Source JSON edits alone do not overwrite an already seeded course.

```powershell
node scripts/author-chapter-two.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/render-chapter-two-audio.ps1
docker compose --env-file .env.mysql -f compose.yml -f compose.chapter-two.yml up -d --build backend
```

## Verification

`ChapterTwoCourseIntegrationTests` verifies seed idempotence, grammar records, all 18 lessons, all 25 quizzes, failed practice, retry, completion requirements, answer-key filtering and unauthenticated WAV delivery. The same tests ran on H2 and an isolated MySQL database. `scripts/verify-chapter-two.cjs` runs the full learner path through the deployed HTTP API with a separate QA account and submits the final at exactly 70%. `--restart-check` verifies the persisted result after a backend restart. Local logs, the QA account and the pre-import SQL backup stay under ignored `.verification/`.

On the local MySQL deployment, the published course has ID `6` and code `VI-GREET-CH02`. The H2 and MySQL integration suites passed (2 tests each). The HTTP verifier completed all 18 lessons and 25 quizzes, passed the 50-question final at exactly 70%, and confirmed the course remained completed after restarting the backend. The database contains one row for this course code; the earlier published courses remain present.
