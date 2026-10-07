package com.sep490.backend;


import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.*;
import com.sep490.backend.dto.model.LearningProgress;
import com.sep490.backend.exception.LearningException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LearnerFeaturesIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired CourseService enrollmentUseCase;
    @Autowired LessonService completeUseCase;
    private final LearnerPrincipal learner = new LearnerPrincipal(1L, "learner@example.test", "", true);
    private final LearnerPrincipal other = new LearnerPrincipal(2L, "other@example.test", "", true);
    private boolean sqlServer;

    @BeforeEach
    void fixture() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            sqlServer = connection.getMetaData().getDatabaseProductName().equals("Microsoft SQL Server");
            boolean isolatedSqlServer = sqlServer && "hola_features_test".equals(connection.getCatalog());
            if (!url.startsWith("jdbc:h2:mem:hola-tests") && !url.matches("jdbc:mysql://[^/]+/hola_features_test(\\?.*)?") && !isolatedSqlServer)
                throw new IllegalStateException("Integration tests require the isolated hola_features_test database.");
        }
        for (String table : List.of("exercise_options", "exercises", "sentence_drills", "dialogue_lines", "dialogues", "course_vocabulary", "lesson_blocks", "vocabulary_entries", "lesson_progress", "enrollments", "lessons", "course_units", "courses", "users_roles", "users"))
            jdbc.update("delete from " + table);
        fixtureInsert("users", "insert into users(id,username,email,password_hash) values(1,?,?,?)", "learner@example.test", "learner@example.test", passwords.encode("Test-password-123"));
        fixtureInsert("users", "insert into users(id,username,email,password_hash) values(2,?,?,?)", "other@example.test", "other@example.test", passwords.encode("Other-password-123"));
        fixtureInsert("courses", "insert into courses(id,title,description,level,estimated_duration,status) values(10,'Beginner Vietnamese','Start here','BEGINNER',30,'PUBLISHED'),(20,'Draft course','Hidden','BEGINNER',10,'DRAFT'),(30,'Empty course','Coming soon','BEGINNER',0,'PUBLISHED')");
        for (int i = 1; i <= 3; i++) fixtureInsert("lessons", "insert into lessons(id,course_id,title,description,lesson_order,content,estimated_duration,published) values(?,10,?,'Description',?,?,10,?)", 100 + i, "Lesson " + i, i, "Xin chào", true);
        fixtureInsert("lessons", "insert into lessons(id,course_id,title,lesson_order,content,estimated_duration,published) values(104,10,'Hidden lesson',4,'Hidden',10,?)", false);
    }
    private void fixtureInsert(String table, String sql, Object... values) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                if (sqlServer) statement.execute("SET IDENTITY_INSERT " + table + " ON");
                try (var insert = connection.prepareStatement(sql)) {
                    for (int i = 0; i < values.length; i++) insert.setObject(i + 1, values[i]);
                    insert.executeUpdate();
                } finally {
                    if (sqlServer) statement.execute("SET IDENTITY_INSERT " + table + " OFF");
                }
            }
            return null;
        });
    }
    private ResultActions enroll() throws Exception {
        return mvc.perform(post("/api/courses/10/enroll").with(user(learner)).with(csrf()));
    }
    private ResultActions complete(long lesson) throws Exception {
        return mvc.perform(post("/api/lessons/" + lesson + "/complete").with(user(learner)).with(csrf()));
    }
    private String vocabulary(Long lesson, String word) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("lessonId", lesson); data.put("word", word); data.put("meaning", "Hello");
        data.put("pronunciation", "sin chow"); data.put("exampleSentence", "Xin chào, tôi tên là John."); data.put("note", "Greeting");
        return json.writeValueAsString(data);
    }
    @Test void catalogExcludesDraftsAndUnpublishedLessons() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/courses/10")).andExpect(jsonPath("$.totalLessons").value(3));
        mvc.perform(get("/api/courses/20")).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/20/enroll").with(user(learner)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/999/enroll").with(user(learner)).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void authenticationAndCsrfProtectMutations() throws Exception {
        mvc.perform(get("/api/me/courses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/courses/10/enroll").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/courses/10/enroll").with(user(learner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/lessons/101").with(user(learner))).andExpect(status().isForbidden());
    }
    @Test void sessionLoginUsesDatabaseAndCsrfTokens() throws Exception {
        var initial = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession();
        var token = json.readTree(initial.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/login").session(session).header("X-CSRF-TOKEN", token)
            .param("username", "learner@example.test").param("password", "Test-password-123")).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/courses").session(session)).andExpect(status().isOk());
        var refreshed = mvc.perform(get("/api/csrf").session(session)).andReturn();
        String newToken = json.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/courses/10/enroll").session(session).header("X-CSRF-TOKEN", newToken)).andExpect(status().isCreated());
        mvc.perform(post("/api/logout").session(session).header("X-CSRF-TOKEN", newToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/courses")).andExpect(status().isUnauthorized());
    }
    @Test void enrollmentAppearsInMyCoursesAndIsIdempotent() throws Exception {
        mvc.perform(get("/api/courses/10/enrollment-status").with(user(learner))).andExpect(status().isNoContent());
        enroll().andExpect(status().isCreated()).andExpect(jsonPath("$.progressPercentage").value(0)).andExpect(jsonPath("$.enrolledAt").exists());
        enroll().andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select count(*) from enrollments", Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/me/courses").with(user(learner))).andExpect(jsonPath("$[0].courseId").value(10)).andExpect(jsonPath("$[0].completedLessons").value(0));
        mvc.perform(get("/api/me/courses").with(user(other))).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void lessonNavigationAndProgressPersistAndCompletionIsIdempotent() throws Exception {
        enroll();
        mvc.perform(get("/api/courses/10/lessons").with(user(learner))).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].learningStatus").value("NOT_STARTED"));
        mvc.perform(post("/api/lessons/101/start").with(user(learner)).with(csrf())).andExpect(jsonPath("$.learningStatus").value("IN_PROGRESS")).andExpect(jsonPath("$.nextLessonId").value(102)).andExpect(jsonPath("$.previousLessonId").isEmpty());
        var first = complete(101).andExpect(jsonPath("$.learningStatus").value("COMPLETED")).andReturn();
        var timestamp = json.readTree(first.getResponse().getContentAsString()).get("completedAt").asText();
        complete(101).andExpect(jsonPath("$.completedAt").value(timestamp));
        mvc.perform(post("/api/lessons/101/start").with(user(learner)).with(csrf())).andExpect(jsonPath("$.learningStatus").value("COMPLETED"));
        mvc.perform(get("/api/me/courses/10/progress").with(user(learner))).andExpect(jsonPath("$.progressPercentage").value(33)).andExpect(jsonPath("$.lastAccessedLessonId").value(101)).andExpect(jsonPath("$.lastAccessedAt").exists());
        mvc.perform(get("/api/lessons/102").with(user(learner))).andExpect(jsonPath("$.previousLessonId").value(101)).andExpect(jsonPath("$.nextLessonId").value(103));
        complete(102); complete(103);
        mvc.perform(get("/api/me/courses/10/progress").with(user(learner))).andExpect(jsonPath("$.progressPercentage").value(100)).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/me/courses").with(user(learner))).andExpect(jsonPath("$[0].completedLessons").value(3)).andExpect(jsonPath("$[0].status").value("COMPLETED"));
        enroll().andExpect(status().isCreated());
    }
    @Test void emptyCourseHasZeroProgressAndNoLessons() throws Exception {
        mvc.perform(post("/api/courses/30/enroll").with(user(learner)).with(csrf())).andExpect(status().isCreated());
        mvc.perform(get("/api/me/courses/30/progress").with(user(learner))).andExpect(jsonPath("$.progressPercentage").value(0)).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/courses/30/lessons").with(user(learner))).andExpect(jsonPath("$.length()").value(0));
        assertThat(new LearningProgress(6, 1).percentage()).isEqualTo(17);
    }
    @Test void inaccessibleLessonsCannotBeReadOrCompleted() throws Exception {
        enroll();
        mvc.perform(get("/api/lessons/104").with(user(learner))).andExpect(status().isNotFound());
        mvc.perform(post("/api/lessons/101/complete").with(user(other)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/courses/10/progress").with(user(other))).andExpect(status().isForbidden());
        jdbc.update("update enrollments set status='CANCELLED' where user_id=1");
        mvc.perform(get("/api/lessons/101").with(user(learner))).andExpect(status().isForbidden());
    }
    @Test void cancelledEnrollmentResumesWithoutErasingHistory() throws Exception {
        enroll(); complete(101);
        jdbc.update("update enrollments set status='CANCELLED' where user_id=1");
        enroll().andExpect(status().isCreated()).andExpect(jsonPath("$.progressPercentage").value(33)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/me/courses/10/progress").with(user(learner))).andExpect(jsonPath("$.lastAccessedLessonId").value(101)).andExpect(jsonPath("$.lessons[0].status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("select count(*) from enrollments", Integer.class)).isEqualTo(1);
    }
    @Test void vocabularyCrudSearchFiltersAndOwnership() throws Exception {
        enroll();
        var result = mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(101L, "Xin chào"))).andExpect(status().isCreated()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(null, "Personal word"))).andExpect(status().isCreated());
        mvc.perform(get("/api/me/vocabulary").with(user(learner))).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/me/vocabulary").param("search", "XIN").param("courseId", "10").param("lessonId", "101").with(user(learner))).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].courseTitle").value("Beginner Vietnamese"));
        mvc.perform(get("/api/me/vocabulary").param("lessonId", "102").with(user(learner))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me/vocabulary").with(user(other))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/me/vocabulary/" + id).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/vocabulary/" + id).with(user(other)).with(csrf()).contentType("application/json").content(vocabulary(101L, "Changed"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/vocabulary/" + id).with(user(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/vocabulary/" + id).with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(102L, "Chào bạn"))).andExpect(status().isOk()).andExpect(jsonPath("$.word").value("Chào bạn"));
        mvc.perform(delete("/api/me/vocabulary/" + id).with(user(learner)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/vocabulary/" + id).with(user(learner))).andExpect(status().isNotFound());
    }
    @Test void vocabularyValidationAndLessonAccessAreEnforced() throws Exception {
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(null, " "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(null, "x".repeat(201)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(999L, "Xin chào"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json").content(vocabulary(101L, "Xin chào"))).andExpect(status().isForbidden());
    }
    @Test void concurrentCompletionsKeepAccurateProgress() throws Exception {
        enroll();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var tasks = List.<Callable<Void>>of(
                () -> { completeUseCase.complete(1L, 101L); return null; },
                () -> { completeUseCase.complete(1L, 102L); return null; },
                () -> { completeUseCase.complete(1L, 103L); return null; });
            for (var future : pool.invokeAll(tasks)) future.get(10, TimeUnit.SECONDS);
        }
        mvc.perform(get("/api/me/courses/10/progress").with(user(learner))).andExpect(jsonPath("$.completedLessons").value(3)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }
    @Test void concurrentEnrollmentsCreateOnlyOneRow() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                try { enrollmentUseCase.enroll(1L, 10L); return true; }
                catch (LearningException | org.springframework.dao.DataIntegrityViolationException e) { return false; }
            };
            var results = pool.invokeAll(List.of(task, task));
            int successes = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(2);
        }
        assertThat(jdbc.queryForObject("select count(*) from enrollments", Integer.class)).isEqualTo(1);
    }
}
