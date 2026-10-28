# Course 01 — Bảng chữ cái và phát âm tiếng Việt

Code: `VI-PRON-CH01`. Level: `A0` (Beginner / Foundation). No prerequisite. Estimated duration: 700 minutes (11 hours 40 minutes).

This is a new course based on the user's Chapter 1 outline supplied on 2026-10-10. It does not replace `VI-PRON-A0` or any other existing course. The outline specifies eight modules without fixed lesson numbers; the material is divided into 34 lessons.

| Module | Lessons | Topic |
| --- | --- | --- |
| 1 | 1–2 | Bảng chữ cái tiếng Việt / The Vietnamese Alphabet |
| 2 | 3–7 | Hệ thống thanh / The Tone System |
| 3 | 8–12 | Nguyên âm đơn / Single Vowels |
| 4 | 13–23 | Tổ hợp nguyên âm / Vowel Combinations |
| 5 | 24–28 | Hệ thống phụ âm / The Consonant System |
| 6 | 29–31 | Phụ âm cuối / Final Consonants |
| 7 | 32–33 | Ảnh hưởng của âm cuối lên a / Finals and a |
| 8 | 34 | Từ Hán-Việt và tổng ôn / Sino-Vietnamese and Review |

## Content and assessments

- All 29 letters: 12 vowel letters and 17 consonant letters. Written letters are distinguished from phonemes, particularly i/y.
- All six tone spellings, the ma/má/mà/mả/mã/mạ comparison, and a regional pronunciation lesson.
- All 33 vowel-combination entries from the outline: 35 spellings after splitting uâ/uơ and ưa/ươ. Every spelling has its supplied example and an assessed recognition item.
- All supplied single-vowel, initial-consonant and final-consonant examples, plus bạc/bạch, hàng/hành and Sino-Vietnamese mã.
- 247 required activities, 177 lesson questions and 50 separately versioned final questions.
- 43 quizzes: 34 lesson quizzes, eight module checkpoints and one course final.
- The final has 50 questions worth two points each. Module question counts: 4, 10, 7, 15, 5, 4, 3, 2. These weights are editorial choices; the new outline did not specify assessment weights.
- Passing score is 70%; retries are unlimited. Required activities and the designated final must pass for course completion.
- Lessons remain accessible in any order, following the current application's policy. No prerequisite locks were added or restored.
- 175 new WAV assets under `/media/pronunciation/chapter01-*.wav`. Audio is Microsoft An synthetic Vietnamese speech, explicitly labelled, not textbook recordings or recordings of native speakers. No microphone scoring is implied.

## Editorial handling

Explanations and assessments are newly authored in Vietnamese and English from the supplied outline. The full textbook and its recordings were not supplied. English comparisons are retained as approximate memory aids, with cautions where necessary.

Vietnamese **th** is taught as aspirated t rather than the English fricative in “theme”. The distinction and regional tone differences are supported by the [Center for Applied Linguistics pronunciation bulletin hosted by ERIC](https://files.eric.ed.gov/fulltext/ED125305.pdf). The [Northern Illinois University pronunciation guide](https://seasite.niu.edu/vietnamese/Guide_to_Pronunciation/pronunciation.htm) provides additional context for reference accents and variation.

The material does not equate tr with English j, does not instruct learners to literally replace a with ai before ch/nh, and distinguishes unreleased final stops from nasal finals. mã = horse and bạch = white are labelled Sino-Vietnamese; everyday horse is ngựa. hửi is marked regional. Medical terms from the outline remain vocabulary examples only.

## Reproduce and maintain

```powershell
node scripts/author-chapter-one.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/render-chapter-one-audio.ps1
docker compose --env-file .env.mysql -f deployment/compose.yml -f deployment/compose.chapter-one.yml up -d --build backend
```

`ChapterOneCourseSeed` reads `src/main/resources/learning/chapter-one-course.json` and writes through the existing authoring services in one transaction. It checks bilingual fields, ordering, coverage, quiz weights and WAV resources. `APP_CHAPTERONE_SEED_ENABLED=true` opts in; an existing ADMIN supplies authorship. It creates no admin credentials.

The seed is idempotent: an existing matching code/structure returns its existing ID without changing learner history. A conflicting structure fails explicitly. JSON changes do not silently overwrite existing database content; subsequent revisions require an explicit migration or admin authoring changes.

The original course files and audio remain in place. The new assets use the already configured pronunciation media route, so no frontend or security changes are needed for this import.

## Verification

`ChapterOneCourseIntegrationTests` checks idempotence, all 34 lessons and 43 quizzes, failed practice and retry, checkpoint/final completion requirements, free lesson order, answer-key filtering and public audio delivery. The MySQL test profile uses the separate `hola_features_test` database.

`node scripts/verify-chapter-one.cjs` checks the deployed APIs with a separate QA learner, completes every lesson and passes the final at exactly 70%. `--restart-check` confirms persistence after restarting the backend. Reports, QA credentials and `before-chapter-one.sql` are kept in ignored `.verification/`.

Verified on 2026-10-10: MySQL course ID **5**, code **VI-PRON-CH01**, status **PUBLISHED**. Both H2 tests and both isolated MySQL tests passed. The real HTTP run completed 34/34 lessons and all 43 quizzes; the final passed at exactly 70%. Audio delivery returned real WAV content. SQL confirmed the 247 activities and all eight final-assessment section weights. Existing course IDs 1–4 remain published.
