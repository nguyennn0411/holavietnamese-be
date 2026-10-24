# Course 03 — Giao tiếp trong nhà hàng

Course code: `VI-RESTAURANT-CH03`. Level: `A1`. Duration: 550 minutes (9 hours 10 minutes). The source is Chapter 3, PDF pages 39–53, of the user-provided *Easy Vietnamese*. The 18 lessons are newly authored around the chapter's teaching points; the textbook's conversations and exercises are not copied.

| Module | Lessons | Nội dung |
| --- | --- | --- |
| 1 | 1–3 | Bắt đầu gọi món |
| 2 | 4–6 | Đơn vị và món ăn |
| 3 | 7–9 | Đồ uống và số cơ bản |
| 4 | 10–12 | Số lượng và đơn gọi |
| 5 | 13–15 | Hương vị và mức độ |
| 6 | 16–18 | Kết thúc bữa ăn |

The curriculum covers requesting a menu, `muốn` and `cho tôi`, restaurant classifiers, noodle and rice dishes, vegetarian requests, drinks, numbers 1–20, `mấy` and `bao nhiêu`, ordering several items, taste words, `rất`, `hơi`, post-adjective `quá`, `có…không`, dessert, `chưa/rồi`, requesting the bill and paying.

**Content:** 18 bilingual lessons, 16 persisted grammar topics with examples, 90 lesson questions, 50 final questions, 100 bundled Vietnamese WAV recordings and 25 quizzes. Each lesson has an explanation, vocabulary, a grammar or communication note, an original dialogue, listening audio, scored matching/listening practice and a quiz. Each module ends with a required checkpoint. The final has 50 questions, 100 points and a passing score of 70%; all quizzes allow retries.

Audio uses the locally installed Microsoft An synthetic Vietnamese voice and is labelled as synthetic speech. It is not the textbook's audio or a human recording. Food and etiquette notes avoid treating regional habits or dietary labels as universal; learners with specific dietary needs are prompted to ask about ingredients.

## Authoring and database

`scripts/author-chapter-three.cjs` writes `src/main/resources/learning/chapter-three-course.json` and its audio manifest. `scripts/render-chapter-three-audio.ps1` creates the WAV files. `ChapterThreeCourseSeed` persists the content through the existing course, grammar, activity, question and quiz services inside a transaction. The optional `APP_CHAPTERTHREE_SEED_ENABLED=true` runner requires an existing ADMIN. Re-running the seed keeps the existing course ID and learner history; a conflicting structure fails explicitly.

```powershell
node scripts/author-chapter-three.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/render-chapter-three-audio.ps1
docker compose --env-file .env.mysql -f compose.yml -f compose.chapter-two.yml -f compose.chapter-three.yml up -d --build backend
```

## Verification

`ChapterThreeCourseIntegrationTests` verifies seed idempotence, 16 grammar records, all 18 lessons, all 25 quizzes, failed practice, retry, completion requirements, answer-key filtering and unauthenticated WAV delivery. The suite passed on H2 and MySQL (2 tests each).

On the local MySQL deployment, the published A1 course has ID `7`, 6 modules, 18 lessons and 25 quizzes. `scripts/verify-chapter-three.cjs` completed the learner path through the deployed HTTP API, passed the 50-question final at exactly 70%, and confirmed the completed 18-lesson progress after a backend restart. A pre-import SQL backup is stored in ignored `.verification/before-chapter-three.sql`.
