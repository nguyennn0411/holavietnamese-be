package com.sep490.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.progress.ActivityProgressService;
import com.sep490.backend.learning.quiz.*;
import com.sep490.backend.learning.shared.*;
import com.sep490.backend.service.CourseService;
import com.sep490.backend.service.LessonService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Member2LearningIntegrationTests {
  @Autowired ContentStore db;
  @Autowired CourseContentService courses;
  @Autowired ActivityService activities;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired ActivityProgressService progress;
  @Autowired QuestionBankService questions;
  @Autowired QuizService quizzes;
  @Autowired CourseService enrollments;
  @Autowired LessonService legacy;
  @Autowired MockMvc mvc;
  @Autowired ContentDeletionController deletion;
  @Autowired com.sep490.backend.learning.grammar.GrammarService grammar;
  long user, other, course, module, lesson, next, text, quizActivity, question, quiz;
  LearnerPrincipal principal;

  @BeforeEach
  void fixture() throws Exception {
    try (var c = jdbc.getDataSource().getConnection()) {
      String url = c.getMetaData().getURL();
      if (!url.startsWith("jdbc:h2:mem:hola-tests")
          && !url.matches("jdbc:mysql://[^/]+/hola_features_test(\\?.*)?"))
        throw new IllegalStateException("Only isolated test databases are allowed.");
    }
    String unique = UUID.randomUUID().toString();
    user =
        db.insert(
            "INSERT INTO users(email,password_hash,enabled) VALUES(?, 'test',true)",
            unique + "@test.local");
    other =
        db.insert(
            "INSERT INTO users(email,password_hash,enabled) VALUES(?, 'test',true)",
            unique + "-other@test.local");
    principal = new LearnerPrincipal(user, unique + "@test.local", "", true);
    course =
        courses.create(
            new ContentRequests.Course(
                unique,
                unique,
                "Test Vietnamese",
                "Description",
                "A1",
                null,
                10,
                "Learn words",
                true),
            user);
    module = courses.addModule(course, new ContentRequests.Module("Module", "Description"));
    lesson =
        courses.addLesson(
            module,
            new ContentRequests.Lesson(
                unique + "-1",
                unique + "-1",
                "Ordering Food",
                "Description",
                "NORMAL",
                5,
                List.of()));
    next =
        courses.addLesson(
            module,
            new ContentRequests.Lesson(
                unique + "-2",
                unique + "-2",
                "Shopping",
                "Description",
                "NORMAL",
                5,
                List.of(lesson)));
    text =
        activities.create(
            lesson,
            new ContentRequests.Activity(
                "TEXT",
                "Introduction",
                "Read",
                db.json("{\"body\":\"Xin chào\"}"),
                true,
                BigDecimal.ZERO,
                "PUBLISHED",
                null,
                List.of()));
    activities.create(
        next,
        new ContentRequests.Activity(
            "TEXT",
            "Next",
            "Read",
            db.json("{\"body\":\"Next\"}"),
            true,
            BigDecimal.ZERO,
            "PUBLISHED",
            null,
            List.of()));
    question = questions.create(question("a"));
    quiz =
        quizzes.create(
            new ContentRequests.Quiz(
                "Food Quiz",
                "Test",
                "LESSON",
                course,
                lesson,
                BigDecimal.valueOf(70),
                10,
                2,
                false));
    quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(question, BigDecimal.TEN));
    quizzes.status(quiz, "PUBLISHED");
    quizActivity =
        activities.create(
            lesson,
            new ContentRequests.Activity(
                "QUIZ",
                "Quiz",
                "Pass",
                db.json("{}"),
                true,
                BigDecimal.TEN,
                "PUBLISHED",
                quiz,
                List.of()));
    courses.lessonStatus(lesson, "PUBLISHED");
    courses.lessonStatus(next, "PUBLISHED");
    courses.status(course, "PUBLISHED");
    enrollments.enroll(user, course);
  }

  private ContentRequests.Question question(String answer) {
    return new ContentRequests.Question(
        "MULTIPLE_CHOICE",
        "Choose tô",
        "A bowl",
        "EASY",
        "Food",
        null,
        null,
        "PUBLISHED",
        db.json("{\"optionIds\":[\"" + answer + "\"]}"),
        db.json(
            "[{\"id\":\"a\",\"text\":\"tô\",\"isCorrect\":true},{\"id\":\"b\",\"text\":\"ly\"}]"));
  }

  private long start() {
    return ((Number) quizzes.start(user, quiz).get("id")).longValue();
  }

  private JsonNode answer(String option) {
    return db.json("{\"optionIds\":[\"" + option + "\"]}");
  }

  @Test
  void enrollIsUniqueAndNewEnrollmentIsNotStarted() {
    assertThat(progress.courseProgress(user, course).get("status")).isEqualTo("NOT_STARTED");
    assertThat(enrollments.enroll(user, course).courseId()).isEqualTo(course);
    assertThat(
            db.count(
                "SELECT count(*) FROM enrollments WHERE user_id=? AND course_id=?", user, course))
        .isEqualTo(1);
  }

  @Test
  void catalogHidesDraftsAndUsesPagination() throws Exception {
    courses.status(course, "DRAFT");
    mvc.perform(get("/api/courses").param("page", "0").param("q", "Test Vietnamese"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(get("/api/courses/" + course)).andExpect(status().isNotFound());
  }

  @Test
  void prerequisitesAreEnforcedByEveryLearningEntry() {
    assertThat(courses.locked(user, next)).isTrue();
    assertThatThrownBy(() -> progress.startLesson(user, next))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("prerequisite");
    assertThatThrownBy(() -> activities.lesson(next, user, false))
        .isInstanceOf(ContentException.class);
  }

  @Test
  void prerequisitesRejectCyclesAndDuplicates() {
    assertThatThrownBy(
            () ->
                courses.editLesson(
                    lesson,
                    new ContentRequests.Lesson(
                        "c", "slug", "Title", "Description", "NORMAL", 1, List.of(next))))
        .hasMessageContaining("Circular");
  }

  @Test
  void textCompletionIsIdempotentAndCannotBypassRequiredQuiz() {
    activities.interact(user, text, null, true);
    activities.interact(user, text, null, true);
    assertThat(
            db.count(
                "SELECT attempts FROM activity_progress WHERE user_id=? AND activity_id=?",
                user,
                text))
        .isEqualTo(1);
    assertThat(progress.lessonProgress(user, lesson).get("completed")).isEqualTo(false);
    assertThatThrownBy(() -> legacy.complete(user, lesson)).hasMessageContaining("required");
    assertThat(activities.interact(user, quizActivity, null, true).get("activityCompleted"))
        .isEqualTo(false);
  }

  @Test
  void enrolledCatalogIncludesDurationAndRealProgress() throws Exception {
    mvc.perform(get("/api/users/me/courses").with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].estimatedDuration").value(10))
        .andExpect(jsonPath("$.content[0].progress.totalLessons").value(2))
        .andExpect(jsonPath("$.content[0].progress.completedLessons").value(0));
  }

  @Test
  void percentageIncludesOptionalActivitiesAndPersistsAfterRequiredCompletion() {
    activities.create(lesson, new ContentRequests.Activity("TEXT", "Optional reading", "",
        db.json("{\"body\":\"Read more\"}"), false, BigDecimal.ZERO, "PUBLISHED", null, List.of()));
    activities.interact(user, text, null, true);
    long attempt = start();
    quizzes.save(user, attempt, question, answer("a"));
    quizzes.submit(user, attempt);
    var result = progress.lessonProgress(user, lesson);
    assertThat(result.get("completed")).isEqualTo(true);
    assertThat(((Number) result.get("progressPercent")).intValue()).isEqualTo(66);
    assertThat(db.count("SELECT progress_percent FROM lesson_progress WHERE lesson_id=?", lesson)).isEqualTo(66);
    assertThat(quizzes.result(user, attempt).get("passingScore")).isNotNull();
  }

  @Test
  void deletionPreservesLearnerHistoryAndRemovesUnusedDrafts() {
    assertThatThrownBy(() -> deletion.course(course)).hasMessageContaining("learning history");
    long unused = quizzes.create(new ContentRequests.Quiz("Unused", "", "PRACTICE", null, null,
        BigDecimal.valueOf(70), null, null, false));
    quizzes.addQuestion(unused, new ContentRequests.QuizQuestion(question, BigDecimal.TEN));
    deletion.quiz(unused);
    assertThat(db.count("SELECT COUNT(*) FROM quizzes WHERE id=?", unused)).isZero();
    assertThat(db.count("SELECT COUNT(*) FROM questions WHERE id=?", question)).isEqualTo(1);
  }

  @Test
  void lessonObjectiveAndGrammarMetadataRoundTrip() {
    courses.editLesson(lesson, new ContentRequests.Lesson("metadata", "metadata", "Title", "Description",
        "NORMAL", 5, List.of(), "Order a meal politely"));
    assertThat(activities.lesson(lesson, user, false).get("learningObjective")).isEqualTo("Order a meal politely");
    long topic = grammar.create(new ContentRequests.Grammar("meta", "meta", "Grammar", "S + V", "Explain",
        "Mistakes", "A1", List.of(), "Short description", "Usage notes"));
    assertThat(grammar.detail(topic, true)).containsEntry("description", "Short description").containsEntry("notes", "Usage notes");
  }

  @Test
  void practiceIsScoredByBackend() {
    long practice =
        activities.create(
            lesson,
            new ContentRequests.Activity(
                "FILL_BLANK",
                "Practice",
                "",
                db.json(
                    "{\"prompt\":\"a"
                        + " bowl\",\"options\":[],\"correctAnswer\":{\"acceptedAnswers\":[\"tô\"]}}"),
                true,
                BigDecimal.TEN,
                "PUBLISHED",
                null,
                List.of()));
    assertThat(
            activities
                .interact(user, practice, db.json("{\"text\":\"wrong\"}"), true)
                .get("activityCompleted"))
        .isEqualTo(false);
    assertThat(
            activities
                .interact(user, practice, db.json("{\"text\":\" TÔ \"}"), true)
                .get("activityCompleted"))
        .isEqualTo(true);
  }

  @Test
  void startResumesAndAnswerPersists() {
    long id = start();
    assertThat(((Number) quizzes.start(user, quiz).get("id")).longValue()).isEqualTo(id);
    quizzes.save(user, id, question, answer("a"));
    assertThat(db.encode(quizzes.attempt(user, id, false)))
        .contains("optionIds")
        .doesNotContain("correctAnswer", "isCorrect", "A bowl");
  }

  @Test
  void learnerPayloadDoesNotLeakScoringBeforeSubmit() throws Exception {
    long id = start();
    var response =
        mvc.perform(get("/api/quiz-attempts/" + id).with(user(principal)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response)
        .doesNotContain(
            "isCorrect",
            "correctAnswer",
            "correctAnswerJson",
            "optionsSnapshotJson",
            "passingScore",
            "A bowl");
    mvc.perform(get("/api/quiz-attempts/" + id + "/result").with(user(principal)))
        .andExpect(status().isConflict());
  }

  @Test
  void otherLearnerCannotReadWriteOrSubmitAttempt() throws Exception {
    long id = start();
    var intruder = new LearnerPrincipal(other, "other@test", "", true);
    mvc.perform(get("/api/quiz-attempts/" + id).with(user(intruder)))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/quiz-attempts/" + id + "/answers/" + question)
                .with(user(intruder))
                .with(csrf())
                .contentType("application/json")
                .content("{\"answer\":{\"optionIds\":[\"a\"]}}"))
        .andExpect(status().isNotFound());
    assertThatThrownBy(() -> quizzes.submit(other, id)).isInstanceOf(ContentException.class);
  }

  @Test
  void snapshotSurvivesEditsBeforeAndAfterSubmission() {
    long id = start();
    questions.edit(question, question("b"));
    quizzes.edit(
        quiz,
        new ContentRequests.Quiz(
            "Changed quiz", "", "LESSON", course, lesson, BigDecimal.valueOf(100), 1, 2, true));
    quizzes.save(user, id, question, answer("a"));
    var result = quizzes.submit(user, id);
    assertThat(result.get("passed")).isEqualTo(true);
    assertThat(result.get("quizTitle")).isEqualTo("Food Quiz");
    assertThat(db.encode(result)).contains("\"optionIds\":[\"a\"]");
    questions.edit(question, question("b"));
    assertThat(db.encode(quizzes.result(user, id))).contains("\"versionNumber\":1");
    long retry = start();
    quizzes.save(user, retry, question, answer("a"));
    assertThat(quizzes.submit(user, retry).get("passed")).isEqualTo(false);
  }

  @Test
  void doubleSubmitRejectedAndMaxAttemptsEnforced() {
    long id = start();
    quizzes.submit(user, id);
    assertThatThrownBy(() -> quizzes.submit(user, id)).hasMessageContaining("already");
    long retry = start();
    assertThat(retry).isNotEqualTo(id);
    quizzes.submit(user, retry);
    assertThatThrownBy(this::start).hasMessageContaining("limit");
  }

  @Test
  void expiredAttemptCannotAcceptNewAnswers() {
    long id = start();
    db.update(
        "UPDATE quiz_attempts SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
        id);
    assertThatThrownBy(() -> quizzes.save(user, id, question, answer("a")))
        .hasMessageContaining("expired");
    assertThat(quizzes.submit(user, id).get("passed")).isEqualTo(false);
  }

  @Test
  void completeLessonUnlocksNextAndCourseRewardsAreOnce() {
    activities.interact(user, text, null, true);
    long id = start();
    quizzes.save(user, id, question, answer("a"));
    quizzes.submit(user, id);
    assertThat(progress.lessonProgress(user, lesson).get("completed")).isEqualTo(true);
    assertThat(courses.locked(user, next)).isFalse();
    long nextActivity = db.count("SELECT id FROM lesson_activities WHERE lesson_id=?", next);
    activities.interact(user, nextActivity, null, true);
    progress.evaluate(user, next);
    assertThat(progress.courseProgress(user, course).get("status")).isEqualTo("COMPLETED");
    assertThat(
            db.count(
                "SELECT count(*) FROM learning_events WHERE user_id=? AND"
                    + " event_type='LESSON_COMPLETED'",
                user))
        .isEqualTo(2);
    assertThat(
            db.count(
                "SELECT count(*) FROM learning_events WHERE user_id=? AND"
                    + " event_type='COURSE_COMPLETED'",
                user))
        .isEqualTo(1);
  }

  @Test
  void adminPreviewDoesNotMutateProgress() throws Exception {
    var admin = new LearnerPrincipal(user, "admin@test", "", true, "ADMIN");
    long before = db.count("SELECT count(*) FROM quiz_attempts");
    mvc.perform(get("/api/admin/lessons/" + lesson + "/preview").with(user(admin)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/quizzes/" + quiz + "/preview").with(user(admin)))
        .andExpect(status().isOk());
    assertThat(db.count("SELECT count(*) FROM quiz_attempts")).isEqualTo(before);
    assertThat(db.count("SELECT count(*) FROM activity_progress WHERE user_id=?", user)).isZero();
    mvc.perform(get("/api/admin/questions").with(user(principal)))
        .andExpect(status().isForbidden());
  }

  @Test
  void reorderRejectsForeignIdsWithoutPartialWrites() {
    assertThatThrownBy(() -> activities.reorder(lesson, List.of(text, 9999999L)))
        .hasMessageContaining("exactly once");
    assertThat(db.count("SELECT order_index FROM lesson_activities WHERE id=?", text)).isEqualTo(1);
    activities.reorder(lesson, List.of(quizActivity, text));
    assertThat(db.count("SELECT order_index FROM lesson_activities WHERE id=?", text)).isEqualTo(2);
  }

  @Test
  void publishingChangesCannotRollbackHistoricalScoring() {
    long id = start();
    quizzes.save(user, id, question, answer("a"));
    courses.status(course, "DRAFT");
    assertThat(quizzes.submit(user, id).get("passed")).isEqualTo(true);
    assertThat(quizzes.result(user, id).get("status")).isEqualTo("SUBMITTED");
    assertThat(
            db.count(
                "SELECT count(*) FROM activity_progress WHERE user_id=? AND activity_id=?",
                user,
                quizActivity))
        .isZero();
  }

  @Test
  void analyticsUsesSubmittedAttemptsAndTheirElapsedTime() {
    long id = start();
    quizzes.save(user, id, question, answer("a"));
    quizzes.submit(user, id);
    db.update("UPDATE quiz_attempts SET started_at=?,submitted_at=? WHERE id=?",
        java.sql.Timestamp.valueOf("2026-10-01 10:00:00"),
        java.sql.Timestamp.valueOf("2026-10-01 10:02:00"), id);
    start(); // An unfinished retry must not affect submitted-only statistics.
    var stats = quizzes.analytics(quiz);
    assertThat(((Number) stats.get("attemptCount")).longValue()).isEqualTo(1);
    assertThat(((Number) stats.get("averageDurationSeconds")).doubleValue()).isEqualTo(120);
    assertThat(((Number) stats.get("averageScore")).doubleValue()).isEqualTo(100);
  }
}
