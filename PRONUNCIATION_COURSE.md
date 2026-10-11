# Bảng chữ cái, thanh điệu và phát âm tiếng Việt

Course code: `VI-PRON-A0`. Level: `A0`. No prerequisite. Estimated study time: 594 minutes (9 hours 54 minutes).

The supplied overview says 24 lessons, but its detailed curriculum enumerates lessons 1–33. This course preserves all 33 lessons in the eight requested modules, in their original order.

| Module | Lessons | Nội dung |
| --- | --- | --- |
| 1 | 1 | Làm quen với hệ thống âm tiếng Việt |
| 2 | 2–4 | Bảng chữ cái tiếng Việt |
| 3 | 5–9 | Nguyên âm |
| 4 | 10–17 | Dấu trên chữ và dấu thanh |
| 5 | 18–20 | Phụ âm đơn |
| 6 | 21–24 | Cụm phụ âm và quy tắc chính tả cơ bản |
| 7 | 25–28 | Cấu trúc một tiếng tiếng Việt |
| 8 | 29–33 | Đọc từ mới từng bước |

## Persisted content

- 240 required activities: explanations, sound patterns, example cards, listening audio, scored practice, listening recognition and quizzes.
- 151 lesson questions plus 50 final questions, stored in the existing question bank with versioned answers.
- 42 quizzes: 33 lesson quizzes, eight module checkpoints and one final assessment.
- 141 bundled WAV audio files, synthesised locally with the installed Microsoft An Vietnamese voice. These are synthetic speech, not human recordings.
- Course/module/lesson explanations, prompts and feedback use Vietnamese and English. Recognition options intentionally preserve the Vietnamese letter or word when a translation would give away the answer.

Final assessment: 50 questions, 100 points. Alphabet 15, vowels 20, consonants 15, tones 25, syllable building 15 and reading 10. Question weights vary from one to three points to match these proportions exactly.

All quizzes require at least 70%, allow retries and preserve completed activities. The first lesson has no prerequisite; every later lesson records the previous lesson as prerequisite metadata, but learners can open published lessons in any order. A checkpoint is a required activity in the final lesson of each module and must pass to complete that lesson. Lesson 17 includes the required tone checkpoint. The final assessment is also designated on the course itself.

## Content corrections and scope

- Corrected `ghe = boat`; `nghe = listen` belongs to the `ngh` lesson.
- Added `na` for the single consonant `n`; explained that `nho` starts with `nh`.
- Explained that `phở` illustrates `ph`, not a standalone initial `p`.
- Explained regional differences, especially hỏi/ngã; written distinctions remain required.
- Uses the current application's `TEXT`, `SOUND_PATTERN`, `VOCABULARY`, `AUDIO`, `PRACTICE`, `LISTEN_AND_CHOOSE` and `QUIZ` renderers. Recognition, matching, tone identification, syllable building and spelling exercises map to its supported question formats.
- Microphone recording, automatic speech scoring, specialised interactive tone charts, adaptive weak-area recommendations and new exercise renderer types are not implemented by this data import. Audio activities instruct learners to listen and repeat. Quiz feedback recommends lessons 14–15 for hỏi/ngã difficulties; this is static guidance, not an AI diagnosis.
- Existing A1 courses and learner history are retained. No new cross-course A1 enrollment prerequisite is introduced.

## Reproduce

Authoring source: `scripts/author-pronunciation.cjs` → `src/main/resources/learning/pronunciation-course.json` and the audio manifest. Learner APIs read the database, not this JSON.

```powershell
node scripts/author-pronunciation.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/render-pronunciation-audio.ps1
docker compose --env-file .env.mysql -f compose.yml -f compose.pronunciation.yml up -d --build backend
```

The seed is opt-in through `APP_PRONUNCIATION_SEED_ENABLED=true` and requires an existing ADMIN author. All inserts run in one transaction. Repeating the seed keeps existing IDs and progress; a conflicting course structure fails explicitly. Editing the JSON does not overwrite an existing course. Later editorial changes need an explicit migration or the admin authoring API.

## Verification

`PronunciationCourseIntegrationTests` seeds twice, checks all counts and the weighted final, completes all 33 lessons and 42 quizzes, and verifies wrong practice answers, failure/retry at every module boundary, required final completion and learner payloads without answer keys.

`node scripts/verify-pronunciation.cjs` exercises the deployed HTTP APIs with a separate QA learner and checks audio delivery. `--restart-check` verifies persisted completion after a backend restart. Local reports, credentials and the pre-import backup stay under ignored `.verification/`.

Verified on 2026-10-07: local MySQL course ID **4**, status **PUBLISHED**. H2 integration tests passed (two tests including public audio delivery and protected admin access); the full curriculum integration test also passed on isolated MySQL. The real HTTP run completed 33/33 lessons and all 42 quizzes, including exactly 70% on the final assessment. After backend restart, progress remained COMPLETED and the seed retained the same course ID without duplicates. Existing course IDs 1–3 remain published. Frontend production build passed with the pre-existing bundle-size warning.

The backend allows public GET only for the new bundled `/media/pronunciation/**` assets in addition to its existing public endpoints. The Vite proxy forwards this path to the backend; production hosting must route the same path. No new media permissions are granted for other directories.
