# Hola Vietnamese learner features

Implemented in order: course enrollment → My Courses → lesson learning → learning progress → vocabulary notebook.

## Setup

SQL Server Docker support was added subsequently. See [SQLSERVER_DOCKER.md](SQLSERVER_DOCKER.md)
for container startup, the `sqlserver` profile, and database-specific migrations. The file inventory below describes the original learner-feature implementation.

The starting repositories were skeletons: no existing User entity, authentication, Course, or Lesson was present.
A minimal `users` table and database-backed Spring Security session login were therefore added.
No registration, administration, AI, analytics, or production sample data was introduced.

### Backend

Requirements: Java 21, Maven, MySQL 8.0+, and an empty database named `hola_vietnamese` with an application user that can run Flyway migrations.

Set these environment variables before starting:

- `DB_URL`: JDBC URL; default `jdbc:mysql://localhost:3306/hola_vietnamese?connectionTimeZone=UTC`.
- `DB_USERNAME`: database user (default `hola`).
- `DB_PASSWORD`: database password.
- `FRONTEND_ORIGIN`: exact allowed browser origin; default `http://localhost:5173`.
- `SESSION_COOKIE_SECURE=true` when serving over HTTPS.
- Optional first-account provisioning: `APP_INITIAL_LEARNER_EMAIL` and `APP_INITIAL_LEARNER_PASSWORD`.
  The password must have at least 12 characters and at most 72 UTF-8 bytes. It is stored using BCrypt.
  Existing accounts are never overwritten. Remove these two variables after provisioning.

From `BE`:

```sh
mvn test
mvn package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Alternatively, `mvn spring-boot:run` works where the runner supports the workspace path.
On this Windows machine, the development runner failed to pass the Unicode workspace path to Java;
the packaged JAR successfully started using the relative path shown above.
Maven is installed at `C:\apache-maven-3.9.12\bin\mvn.cmd` but was not on PATH during verification.

Flyway creates the schema and Hibernate validates it. The database starts without courses.
Provision real course/lesson content through your content pipeline or database tooling.
A course uses `PUBLISHED`, `DRAFT`, or `ARCHIVED`; only published courses and lessons are available for learning.
Lesson content is displayed as safe plain text with preserved line breaks. Media URLs should reference browser-playable video/audio files.

### Frontend

From `FE/holavietnamese_fe`:

```sh
npm ci
npm run dev
npm run build
```

The default API base is `/api`. Vite proxies it to `http://localhost:8080`, keeping session cookies on the same browser origin.
For production, configure the web server to serve the React SPA for frontend routes and proxy `/api` to Spring.
If using a separate API origin, configure `VITE_API_BASE_URL` and `FRONTEND_ORIGIN`; use compatible same-site hosting for session cookies.
The original React Router dependency `^8.4.0` was unavailable in the registry. It was corrected to published `^7.18.4`, and a lockfile was added.
No API failure is silently replaced with mock data.

## API endpoints

All paths below include the `/api` prefix. Identifiers belong to the authenticated learner, never a user ID supplied in the request body.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | /api/courses | Published course catalog (public) |
| GET | /api/courses/{courseId} | Available course detail (public) |
| GET | /api/courses/{courseId}/enrollment-status | Current learner enrollment; 204 if none |
| POST | /api/courses/{courseId}/enroll | Enroll; 201, or 409 for an existing active/completed enrollment |
| GET | /api/me/courses | Current learner's non-cancelled courses and progress |
| GET | /api/courses/{courseId}/lessons | Ordered lesson metadata/status for an enrolled learner |
| GET | /api/lessons/{lessonId} | Lesson content, timestamps, previous/next IDs |
| POST | /api/lessons/{lessonId}/start | Start or revisit lesson; update last access |
| POST | /api/lessons/{lessonId}/complete | Complete idempotently and recalculate course status |
| GET | /api/me/courses/{courseId}/progress | Course summary and individual lesson states |
| POST | /api/me/vocabulary | Save vocabulary; optional lesson association |
| GET | /api/me/vocabulary | Own entries; combine search, courseId, lessonId |
| GET | /api/me/vocabulary/{id} | Read owned entry |
| PUT | /api/me/vocabulary/{id} | Replace owned entry's editable fields |
| DELETE | /api/me/vocabulary/{id} | Delete owned entry; 204 |
| GET | /api/csrf | Session CSRF token/header name (public) |
| POST | /api/login | Form-urlencoded username/password; establishes session, 204 |
| POST | /api/logout | Invalidate session, 204 |
| GET | /api/me/session | Current learner ID/email |

Mutation requests require the CSRF token from `/api/csrf`.
The HTTP client handles cookies and tokens, resetting its token after login/logout or expired sessions.
Controllers call input ports. Spring Security handles authentication; controllers do not read JpaRepositories.
Bean Validation and the global handler return 400 for invalid requests, 403 for unavailable enrollment access,
404 for missing/unowned records, and 409 for conflicts.

## Database entities and relationships

| Domain / persistence entity | Table | Relations / constraints |
| --- | --- | --- |
| UserJpaEntity | users | Unique email; BCrypt password hash |
| Course / CourseJpaEntity | courses | One course has many lessons and enrollments |
| Lesson / LessonJpaEntity | lessons | Course foreign key; unique course/lesson order |
| Enrollment / EnrollmentJpaEntity | enrollments | User/course foreign keys; unique (user_id, course_id); last lesson foreign key |
| LessonProgress / LessonProgressJpaEntity | lesson_progress | Enrollment/lesson foreign keys; unique (enrollment_id, lesson_id) |
| VocabularyEntry / VocabularyJpaEntity | vocabulary_entries | User foreign key, optional lesson foreign key; user/creation index |

`LearningProgress` is a derived domain value, not an extra database table.
Progress is the rounded percentage of completed published lessons; a zero-lesson course stays at 0%.
Repeated completion preserves its original timestamp. Starting a completed lesson does not downgrade it.
Progress mutations serialize on the enrollment with a pessimistic lock and READ_COMMITTED isolation,
so MySQL transactions see preceding completions. Unique constraints also protect concurrent enrollment.
Cancelled enrollment can reuse its unique row with fresh progress; completed enrollment remains available for review.
No enrollment cancellation API is added.

## Frontend routes

| Route | Page |
| --- | --- |
| / | HomePage |
| /courses | CourseListPage |
| /courses/:courseId | CourseDetailPage |
| /my-courses | MyCoursesPage |
| /learn/:courseId/lesson/:lessonId | LessonLearningPage |
| /progress | LearningProgressPage |
| /vocabulary | VocabularyNotebookPage |
| /learn/:courseId | ResumeCoursePage: last accessed lesson, otherwise first unfinished/first available |
| /login | LoginPage |
| * | Existing NotFoundPage |

The learner navigation includes Home, Courses, My Courses, Progress, and Vocabulary Notebook.
Course details show a curriculum and progress for enrolled learners.
Components provide loading, error, retry, unauthorized, and empty states.
Vocabulary dialogs use native modal focus containment, support Escape, and preserve validation errors.
Async resources discard stale results after navigation; lesson IDs are checked against the selected course before starting.

## Data flow

React Page → Presentation Hook → Application Use Case → Repository Interface →
Repository Implementation → HTTP Client → Spring REST Controller → Input Port / Application Use Case →
Repository Port → Persistence Adapter → Spring Data JPA → MySQL.

The domain model is framework independent. Composition lives in frontend `app/services.js`.
The reusable ProgressBar is used across course detail, My Courses, learning, and the progress dashboard.
All progress and vocabulary changes are persisted by the backend; React state holds only the current view/form.

## Verification

- Frontend production build passed with Vite.
- Backend executable JAR packaging passed.
- 13 backend tests passed on isolated H2 and again on a fresh MySQL 8.0.46 instance.
- Flyway migrations and Hibernate schema validation passed on both databases.
- Tests cover authentication/CSRF/session login, hidden courses and lessons, duplicate/concurrent enrollment,
  My Courses ownership, zero-lesson courses, start/revisit/completion, timestamp idempotency, rounded progress,
  previous/next IDs, completed status, cancellation/re-enrollment, concurrent lesson completion,
  vocabulary validation, search/filter combinations, CRUD, and cross-user access denial.
- Browser checks against the real API verified sign-in, course detail/curriculum/progress, lesson navigation,
  and saving/editing/searching/filtering vocabulary. No browser API mock was used.
- Narrow-screen layout was visually inspected; the desktop homepage reported no horizontal overflow at 1280px.
- Git whitespace checks passed.

Run `mvn test` for isolated H2 tests. To repeat MySQL integration tests, create a fresh, disposable
database named `hola_features_test` and override Spring datasource URL, driver, username, and password:

```sh
mvn test -Dspring.datasource.url=jdbc:mysql://127.0.0.1:33316/hola_features_test -Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver -Dspring.datasource.username=root
```

Integration fixtures delete their own tables before each case. A database-name guard rejects other MySQL database names.
Do not point these tests at a database containing real data.
Test credentials/content exist only in integration fixtures and the isolated verification database.
The user's existing MySQL service was not started or modified.
The temporary MySQL instance is stopped and its disposable data directory was removed after verification.
Recreate an isolated database to repeat those tests; no production or pre-existing data was deleted.

## Files created

Paths are relative to the shared workspace. Build artifacts, dependency caches, and temporary verification data are excluded.

- `BE/src/main/java/com/sep490/backend/controller/CourseController.java`
- `BE/src/main/java/com/sep490/backend/controller/LearningProgressController.java`
- `BE/src/main/java/com/sep490/backend/controller/LessonController.java`
- `BE/src/main/java/com/sep490/backend/controller/MyCoursesController.java`
- `BE/src/main/java/com/sep490/backend/controller/SessionController.java`
- `BE/src/main/java/com/sep490/backend/controller/VocabularyController.java`
- `BE/src/main/java/com/sep490/backend/repository/CourseRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/EnrollmentRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/LessonRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/LessonProgressRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/VocabularyRepository.java`
- `BE/src/main/java/com/sep490/backend/entity/CourseJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/entity/EnrollmentJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/entity/LessonJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/entity/LessonProgressJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/entity/UserJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/entity/VocabularyJpaEntity.java`
- `BE/src/main/java/com/sep490/backend/mapper/CourseMapper.java`
- `BE/src/main/java/com/sep490/backend/mapper/EnrollmentMapper.java`
- `BE/src/main/java/com/sep490/backend/mapper/LessonMapper.java`
- `BE/src/main/java/com/sep490/backend/mapper/VocabularyMapper.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/CourseJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/EnrollmentJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/LessonJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/LessonProgressJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/UserJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/repository/jpa/VocabularyJpaRepository.java`
- `BE/src/main/java/com/sep490/backend/dto/request/VocabularyRequest.java`
- `BE/src/main/java/com/sep490/backend/dto/response/CourseResponse.java`
- `BE/src/main/java/com/sep490/backend/dto/response/EnrollmentResponse.java`
- `BE/src/main/java/com/sep490/backend/dto/response/LearningProgressResponse.java`
- `BE/src/main/java/com/sep490/backend/dto/response/LessonResponse.java`
- `BE/src/main/java/com/sep490/backend/dto/response/MyCourseResponse.java`
- `BE/src/main/java/com/sep490/backend/dto/response/VocabularyResponse.java`
- `BE/src/main/java/com/sep490/backend/service/CourseService.java`
- `BE/src/main/java/com/sep490/backend/service/LearnerAccess.java`
- `BE/src/main/java/com/sep490/backend/service/LearningProgressService.java`
- `BE/src/main/java/com/sep490/backend/service/LessonService.java`
- `BE/src/main/java/com/sep490/backend/service/MyCoursesService.java`
- `BE/src/main/java/com/sep490/backend/service/VocabularyService.java`
- `BE/src/main/java/com/sep490/backend/dto/model/Course.java`
- `BE/src/main/java/com/sep490/backend/dto/model/Enrollment.java`
- `BE/src/main/java/com/sep490/backend/dto/model/LearningProgress.java`
- `BE/src/main/java/com/sep490/backend/dto/model/Lesson.java`
- `BE/src/main/java/com/sep490/backend/dto/model/LessonProgress.java`
- `BE/src/main/java/com/sep490/backend/dto/model/VocabularyEntry.java`
- `BE/src/main/java/com/sep490/backend/exception/LearningException.java`
- `BE/src/main/java/com/sep490/backend/entity/enums/CourseStatus.java`
- `BE/src/main/java/com/sep490/backend/entity/enums/EnrollmentStatus.java`
- `BE/src/main/java/com/sep490/backend/entity/enums/LessonStatus.java`
- `BE/src/main/java/com/sep490/backend/config/InitialLearnerConfig.java`
- `BE/src/main/java/com/sep490/backend/config/LearnerPrincipal.java`
- `BE/src/main/java/com/sep490/backend/config/SecurityConfig.java`
- `BE/src/main/java/com/sep490/backend/exception/GlobalExceptionHandler.java`
- `BE/src/main/resources/db/migration/V1__courses_and_enrollments.sql`
- `BE/src/main/resources/db/migration/V2__vocabulary_notebook.sql`
- `BE/src/test/java/com/sep490/backend/LearnerFeaturesIntegrationTests.java`
- `BE/src/test/resources/application.yml`
- `BE/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `BE/LEARNER_FEATURES.md`

- `FE/holavietnamese_fe/package-lock.json`
- `FE/holavietnamese_fe/src/app/services.js`
- `FE/holavietnamese_fe/src/app/session.js`
- `FE/holavietnamese_fe/src/application/usecases/courseUseCases.js`
- `FE/holavietnamese_fe/src/application/usecases/learningUseCases.js`
- `FE/holavietnamese_fe/src/application/usecases/vocabularyUseCases.js`
- `FE/holavietnamese_fe/src/domain/entities/Course.js`
- `FE/holavietnamese_fe/src/domain/entities/Enrollment.js`
- `FE/holavietnamese_fe/src/domain/entities/LearningProgress.js`
- `FE/holavietnamese_fe/src/domain/entities/Lesson.js`
- `FE/holavietnamese_fe/src/domain/entities/VocabularyEntry.js`
- `FE/holavietnamese_fe/src/domain/repositories/CourseRepository.js`
- `FE/holavietnamese_fe/src/domain/repositories/LearningRepository.js`
- `FE/holavietnamese_fe/src/domain/repositories/VocabularyRepository.js`
- `FE/holavietnamese_fe/src/infrastructure/repositories/CourseRepositoryImpl.js`
- `FE/holavietnamese_fe/src/infrastructure/repositories/LearningRepositoryImpl.js`
- `FE/holavietnamese_fe/src/infrastructure/repositories/VocabularyRepositoryImpl.js`
- `FE/holavietnamese_fe/src/presentation/components/common/Modal.jsx`
- `FE/holavietnamese_fe/src/presentation/components/common/ProgressBar.jsx`
- `FE/holavietnamese_fe/src/presentation/components/common/ResourceState.jsx`
- `FE/holavietnamese_fe/src/presentation/components/common/SessionNavigation.jsx`
- `FE/holavietnamese_fe/src/presentation/components/course/CourseCard.jsx`
- `FE/holavietnamese_fe/src/presentation/components/course/CourseCurriculum.jsx`
- `FE/holavietnamese_fe/src/presentation/components/course/CourseInfo.jsx`
- `FE/holavietnamese_fe/src/presentation/components/course/CourseProgress.jsx`
- `FE/holavietnamese_fe/src/presentation/components/course/EnrollButton.jsx`
- `FE/holavietnamese_fe/src/presentation/components/learning/LessonContent.jsx`
- `FE/holavietnamese_fe/src/presentation/components/learning/LessonNavigation.jsx`
- `FE/holavietnamese_fe/src/presentation/components/learning/LessonSidebar.jsx`
- `FE/holavietnamese_fe/src/presentation/components/learning/LessonStatus.jsx`
- `FE/holavietnamese_fe/src/presentation/components/learning/MediaPlayer.jsx`
- `FE/holavietnamese_fe/src/presentation/components/my-courses/CourseProgressBar.jsx`
- `FE/holavietnamese_fe/src/presentation/components/my-courses/MyCourseCard.jsx`
- `FE/holavietnamese_fe/src/presentation/components/vocabulary/VocabularyCard.jsx`
- `FE/holavietnamese_fe/src/presentation/components/vocabulary/VocabularyFilter.jsx`
- `FE/holavietnamese_fe/src/presentation/components/vocabulary/VocabularyForm.jsx`
- `FE/holavietnamese_fe/src/presentation/components/vocabulary/VocabularySearch.jsx`
- `FE/holavietnamese_fe/src/presentation/hooks/useAsyncResource.js`
- `FE/holavietnamese_fe/src/presentation/hooks/useCourseProgress.js`
- `FE/holavietnamese_fe/src/presentation/hooks/useCourses.js`
- `FE/holavietnamese_fe/src/presentation/hooks/useLearning.js`
- `FE/holavietnamese_fe/src/presentation/hooks/useMyCourses.js`
- `FE/holavietnamese_fe/src/presentation/hooks/useVocabulary.js`
- `FE/holavietnamese_fe/src/presentation/pages/auth/LoginPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/courses/CourseDetailPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/courses/CourseListPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/learning/LessonLearningPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/learning/ResumeCoursePage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/my-courses/MyCoursesPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/progress/LearningProgressPage.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/vocabulary/VocabularyNotebookPage.jsx`

## Files modified

- `BE/.gitignore`
- `BE/README.md`
- `BE/pom.xml`
- `BE/src/main/resources/application.yml`

- `FE/holavietnamese_fe/.env.example`
- `FE/holavietnamese_fe/README.md`
- `FE/holavietnamese_fe/index.html`
- `FE/holavietnamese_fe/package.json`
- `FE/holavietnamese_fe/src/app/router/AppRouter.jsx`
- `FE/holavietnamese_fe/src/infrastructure/api/httpClient.js`
- `FE/holavietnamese_fe/src/presentation/layouts/MainLayout.jsx`
- `FE/holavietnamese_fe/src/presentation/pages/HomePage.jsx`
- `FE/holavietnamese_fe/src/shared/constants/routes.js`
- `FE/holavietnamese_fe/src/styles.css`
- `FE/holavietnamese_fe/vite.config.js`
