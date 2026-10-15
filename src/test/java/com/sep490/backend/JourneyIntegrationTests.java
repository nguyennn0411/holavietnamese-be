package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.request.*;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.service.DestinationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.sep490.backend.repository.jpa.PassportEntryJpaRepository;
import com.sep490.backend.entity.PassportEntryJpaEntity;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class JourneyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DestinationService destinations;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoSpyBean PassportEntryJpaRepository stamps;
    private final LearnerPrincipal learner = new LearnerPrincipal(820001L, "journey-one@example.test", "", true);
    private final LearnerPrincipal other = new LearnerPrincipal(820002L, "journey-two@example.test", "", true);
    private final LearnerPrincipal admin = new LearnerPrincipal(820003L, "journey-admin@example.test", "", true, "ADMIN");

    @BeforeEach void fixture() {
        for (var p : List.of(learner, other, admin))
            jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(?,?,?,?)", p.id(), p.email(), p.email(), "unused-test-hash");
    }
    private long destination(String name, VietnamRegion region, DestinationStatus status) {
        return destinations.create(new DestinationRequest(name, name + " English", name.toLowerCase(Locale.ROOT), region,
                null, null, null, null, null, status)).id();
    }
    private JsonNode result(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("result"); }
    private ResultActions configure(List<Map<String, Object>> stops) throws Exception {
        return mvc.perform(put("/api/admin/journey").with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("stops", stops))));
    }
    private Map<String, Object> stop(long id, String status) { return Map.of("destinationId", id, "status", status); }
    private ResultActions content(long id, List<Long> lessons, List<Long> words) throws Exception {
        return mvc.perform(put("/api/admin/destinations/" + id + "/content").with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("lessonIds", lessons, "vocabularyIds", words))));
    }
    private long course(String status) {
        jdbc.update("INSERT INTO courses(title,description,level,estimated_duration,status) VALUES('Journey test','test','A1',10,?)", status);
        return jdbc.queryForObject("SELECT MAX(id) FROM courses", Long.class);
    }
    private long lesson(long course, int order, boolean published) {
        jdbc.update("INSERT INTO lessons(course_id,title,description,lesson_order,content,estimated_duration,published,publication_status) VALUES(?,'Journey lesson','test',?,'test',10,?,?)", course, order, published, published ? "PUBLISHED" : "DRAFT");
        return jdbc.queryForObject("SELECT MAX(id) FROM lessons", Long.class);
    }
    private long word(String status) {
        jdbc.update("INSERT INTO vocabularies(word,part_of_speech,cefr_level,status) VALUES('xin chào','PHRASE','A1',?)", status);
        return jdbc.queryForObject("SELECT MAX(id) FROM vocabularies", Long.class);
    }
    private ResultActions start(long id, LearnerPrincipal principal) throws Exception {
        return mvc.perform(post("/api/me/journey/destinations/" + id + "/start").with(user(principal)).with(csrf()).contentType("application/json").content("{}"));
    }
    private ResultActions complete(long id, LearnerPrincipal principal) throws Exception {
        return mvc.perform(post("/api/me/journey/destinations/" + id + "/complete").with(user(principal)).with(csrf()).contentType("application/json").content("{\"completed\":true,\"progressPercent\":100,\"userId\":820002}"));
    }
    private void learned(long course, long lesson, LearnerPrincipal principal) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id=? AND course_id=?", Integer.class, principal.id(), course) == 0)
            jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES(?,?,'ACTIVE',CURRENT_TIMESTAMP)", principal.id(), course);
        long enrollment = jdbc.queryForObject("SELECT id FROM enrollments WHERE user_id=? AND course_id=?", Long.class, principal.id(), course);
        jdbc.update("INSERT INTO lesson_progress(enrollment_id,lesson_id,status,completed_at) VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP)", enrollment, lesson);
    }

    @Test void configurationPersistsExactOrderAndFiltersBeforeChoosingStartPoint() throws Exception {
        long z = destination("Zulu", VietnamRegion.SOUTH, DestinationStatus.PUBLISHED);
        long a = destination("Alpha", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        long draft = destination("Draft", VietnamRegion.CENTRAL, DestinationStatus.DRAFT);
        configure(List.of(stop(draft, "PUBLISHED"), stop(z, "PUBLISHED"), stop(a, "PUBLISHED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.stops[1].destination.id").value(z));
        em.flush(); em.clear();
        mvc.perform(get("/api/admin/journey").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stops[0].destination.id").value(draft));
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalStops").value(2))
                .andExpect(jsonPath("$.result.stops[0].destination.id").value(z))
                .andExpect(jsonPath("$.result.stops[0].startPoint").value(true))
                .andExpect(jsonPath("$.result.stops[1].startPoint").value(false));
        mvc.perform(get("/api/me/journey").with(user(learner)).param("region", "NORTH")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalStops").value(2)).andExpect(jsonPath("$.result.stops[0].startPoint").value(false));
        configure(List.of(stop(a, "PUBLISHED"), stop(z, "ARCHIVED"))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(learner)).param("keyword", "alpha"))
                .andExpect(jsonPath("$.result.stops.length()").value(1)).andExpect(jsonPath("$.result.stops[0].destination.id").value(a));
    }
    @Test void invalidConfigurationAndUnauthorizedWritesLeaveSavedOrderUntouched() throws Exception {
        long id = destination("Valid", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        configure(List.of(stop(id, "PUBLISHED"), stop(id, "PUBLISHED"))).andExpect(status().isBadRequest());
        configure(List.of(stop(999999L, "PUBLISHED"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/journey").with(user(learner)).with(csrf()).contentType("application/json").content("{\"stops\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/journey").with(user(admin))).andExpect(jsonPath("$.result.stops[0].destination.id").value(id));
        mvc.perform(get("/api/me/journey")).andExpect(status().isUnauthorized());
    }
    @Test void linksValidateRealReferencesAndPublicContentWithoutChangingSourceRecords() throws Exception {
        long id = destination("Linked", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        long publicCourse = course("PUBLISHED"), hiddenCourse = course("DRAFT");
        long published = lesson(publicCourse, 1, true), draft = lesson(publicCourse, 2, false), hidden = lesson(hiddenCourse, 1, true);
        long publicWord = word("PUBLISHED"), draftWord = word("DRAFT");
        content(id, List.of(published, draft, hidden), List.of(publicWord, draftWord)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lessons.length()").value(3));
        mvc.perform(get("/api/me/journey/destinations/" + id + "/content").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.lessons.length()").value(1))
                .andExpect(jsonPath("$.result.lessons[0].id").value(published)).andExpect(jsonPath("$.result.vocabulary.length()").value(1));
        content(id, List.of(published, published), List.of()).andExpect(status().isBadRequest());
        content(id, List.of(999999L), List.of(publicWord)).andExpect(status().isNotFound());
        em.flush(); em.clear();
        mvc.perform(get("/api/admin/destinations/" + id + "/content").with(user(admin))).andExpect(jsonPath("$.result.lessons.length()").value(3));
        content(id, List.of(draft, published), List.of(publicWord)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lessons[0].id").value(draft));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lessons WHERE course_id IN (?,?)", Integer.class, publicCourse, hiddenCourse)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies", Integer.class)).isEqualTo(2);
    }
    @Test void noLessonRejectsClaimedCompletionAndNeverIssuesProgressOrStamp() throws Exception {
        long id = destination("Pending", VietnamRegion.CENTRAL, DestinationStatus.PUBLISHED);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(jsonPath("$.result.rulesConfigured").value(true));
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/me/journey/destinations/" + id + "/complete").with(user(learner)).with(csrf())
                    .contentType("application/json").content("{\"completed\":true,\"progressPercent\":100,\"userId\":820002}"))
                    .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passport_entries", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_journey_progress", Integer.class)).isZero();
    }
    @Test void progressAndHistoricalPassportStayPrivateAndSurviveRemovingOrArchivingStop() throws Exception {
        long id = destination("Historic", VietnamRegion.SOUTH, DestinationStatus.PUBLISHED);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        jdbc.update("INSERT INTO user_journey_progress(user_id,destination_id,status,progress_percent,completed_at) VALUES(?,?,'COMPLETED',100,CURRENT_TIMESTAMP)", learner.id(), id);
        jdbc.update("INSERT INTO user_passport_entries(user_id,destination_id,stamped_at) VALUES(?,?,CURRENT_TIMESTAMP)", learner.id(), id);
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(jsonPath("$.result.completedStops").value(1));
        mvc.perform(get("/api/me/journey").with(user(other)).param("userId", learner.id().toString())).andExpect(jsonPath("$.result.completedStops").value(0));
        mvc.perform(get("/api/me/journey/passport").with(user(other))).andExpect(jsonPath("$.result[0].unlocked").value(false));
        configure(List.of()).andExpect(status().isOk());
        jdbc.update("UPDATE destinations SET status='ARCHIVED' WHERE id=?", id); em.clear();
        mvc.perform(get("/api/me/journey/passport").with(user(learner))).andExpect(jsonPath("$.result[0].destinationId").value(id))
                .andExpect(jsonPath("$.result[0].unlocked").value(true)).andExpect(jsonPath("$.result[0].stampedAt").isNotEmpty());
        mvc.perform(get("/api/me/journey/passport").with(user(other))).andExpect(jsonPath("$.result.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_journey_progress WHERE user_id=?", Integer.class, learner.id())).isEqualTo(1);
    }
    @Test void lessonCompletionAndOverviewReadOnlyCurrentLearnerRecords() throws Exception {
        long id = destination("Learning", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        long course = course("PUBLISHED"), lesson = lesson(course, 1, true);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        content(id, List.of(lesson), List.of()).andExpect(status().isOk());
        jdbc.update("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES(?,?,'ACTIVE',CURRENT_TIMESTAMP)", learner.id(), course);
        long enrollment = jdbc.queryForObject("SELECT id FROM enrollments WHERE user_id=? AND course_id=?", Long.class, learner.id(), course);
        jdbc.update("INSERT INTO lesson_progress(enrollment_id,lesson_id,status,completed_at) VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP)", enrollment, lesson);
        jdbc.update("INSERT INTO user_journey_progress(user_id,destination_id,status,progress_percent,started_at) VALUES(?,?,'IN_PROGRESS',50,CURRENT_TIMESTAMP)", learner.id(), id);
        mvc.perform(get("/api/me/journey/destinations/" + id + "/content").with(user(learner))).andExpect(jsonPath("$.result.lessons[0].completed").value(true));
        mvc.perform(get("/api/me/journey/destinations/" + id + "/content").with(user(other))).andExpect(jsonPath("$.result.lessons[0].completed").value(false));
        var overview = result(mvc.perform(get("/api/users/progress").with(user(learner))).andExpect(status().isOk()).andReturn());
        assertThat(overview.path("journeyCity").asText()).isEqualTo("Learning");
        assertThat(overview.path("journeyProgress").asInt()).isZero();
    }
    @Test void overviewCombinesJourneyNotebookAndXpWithoutInventedMetrics() throws Exception {
        long id = destination("Overview", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        jdbc.update("INSERT INTO vocabulary_entries(user_id,word,meaning,created_at) VALUES(?,'xin chào','hello',CURRENT_TIMESTAMP)", learner.id());
        jdbc.update("INSERT INTO vocabulary_entries(user_id,word,meaning,created_at) VALUES(?,'private','other learner',CURRENT_TIMESTAMP)", other.id());
        jdbc.update("UPDATE users SET total_xp=35,streak_count=2,daily_learning_goal_minutes=25 WHERE id=?", learner.id());
        jdbc.update("INSERT INTO xp_transactions(user_id,amount,event_type,description) VALUES(?,35,'LESSON_COMPLETE','Completed a lesson')", learner.id());
        em.clear();

        mvc.perform(get("/api/users/progress").with(user(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.journeyCity").value("Overview"))
                .andExpect(jsonPath("$.result.journeyProgress").value(0))
                .andExpect(jsonPath("$.result.savedVocabCount").value(1))
                .andExpect(jsonPath("$.result.learnedVocabulariesCount").value(1))
                .andExpect(jsonPath("$.result.recentVocab[0].vi").value("xin chào"))
                .andExpect(jsonPath("$.result.completedLessonsCount").value(0))
                .andExpect(jsonPath("$.result.currentStreak").value(2))
                .andExpect(jsonPath("$.result.streakCount").value(2))
                .andExpect(jsonPath("$.result.totalXp").value(35))
                .andExpect(jsonPath("$.result.dailyGoalMinutes").value(25))
                .andExpect(jsonPath("$.result.recentActivities[0].amount").value(35))
                .andExpect(jsonPath("$.result.recentXpTransactions[0].description").value("Completed a lesson"))
                .andExpect(jsonPath("$.result.masteredWords").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result.totalTimeMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.result.weeklyActivity").isEmpty());
        mvc.perform(get("/api/users/progress").with(user(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalXp").value(0))
                .andExpect(jsonPath("$.result.recentActivities").isEmpty());
    }
    @Test void lockedStopsRejectDirectReadsAndWritesAndStartPersistsOnlyCurrentUser() throws Exception {
        long first = destination("First", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        long next = destination("Next", VietnamRegion.SOUTH, DestinationStatus.PUBLISHED);
        configure(List.of(stop(first, "PUBLISHED"), stop(next, "PUBLISHED"))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(jsonPath("$.result.currentDestinationId").value(first))
                .andExpect(jsonPath("$.result.stops[0].status").value("CURRENT")).andExpect(jsonPath("$.result.stops[1].status").value("LOCKED"));
        mvc.perform(get("/api/destinations/" + next).with(user(learner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/journey/destinations/" + next + "/content").with(user(learner))).andExpect(status().isForbidden());
        start(next, learner).andExpect(status().isForbidden()); complete(next, learner).andExpect(status().isForbidden());
        start(first, learner).andExpect(status().isOk()).andExpect(jsonPath("$.result.journey.startedAt").isNotEmpty());
        start(first, learner).andExpect(status().isOk()); em.flush(); em.clear();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_journey_progress WHERE user_id=?", Integer.class, learner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_journey_progress WHERE user_id=?", Integer.class, other.id())).isZero();
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(jsonPath("$.result.stops[0].startedAt").isNotEmpty());
    }
    @Test void allLinkedLessonsAreRequiredBeforeAtomicCompletionStampAndNextUnlock() throws Exception {
        long first = destination("LearnAll", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
        long next = destination("Continue", VietnamRegion.SOUTH, DestinationStatus.PUBLISHED);
        long course = course("PUBLISHED"), a = lesson(course, 1, true), b = lesson(course, 2, true);
        configure(List.of(stop(first, "PUBLISHED"), stop(next, "PUBLISHED"))).andExpect(status().isOk());
        content(first, List.of(a, b), List.of()).andExpect(status().isOk());
        start(first, learner).andExpect(status().isOk()); learned(course, a, learner);
        mvc.perform(get("/api/me/journey/destinations/" + first + "/content").with(user(learner)))
                .andExpect(jsonPath("$.result.journey.progressPercent").value(50)).andExpect(jsonPath("$.result.journey.canComplete").value(false));
        complete(first, learner).andExpect(status().isConflict());
        learned(course, b, learner);
        complete(first, learner).andExpect(status().isOk()).andExpect(jsonPath("$.result.progressPercent").value(50))
                .andExpect(jsonPath("$.result.stops[0].status").value("COMPLETED")).andExpect(jsonPath("$.result.stops[1].status").value("CURRENT"));
        var completedAt = jdbc.queryForObject("SELECT completed_at FROM user_journey_progress WHERE user_id=? AND destination_id=?", java.sql.Timestamp.class, learner.id(), first);
        var stampedAt = jdbc.queryForObject("SELECT stamped_at FROM user_passport_entries WHERE user_id=? AND destination_id=?", java.sql.Timestamp.class, learner.id(), first);
        assertThat(stampedAt).isEqualTo(completedAt);
        complete(first, learner).andExpect(status().isOk()); complete(first, learner).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passport_entries WHERE user_id=?", Integer.class, learner.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT completed_at FROM user_journey_progress WHERE user_id=? AND destination_id=?", java.sql.Timestamp.class, learner.id(), first)).isEqualTo(completedAt);
        mvc.perform(get("/api/destinations/" + next).with(user(learner))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(other))).andExpect(jsonPath("$.result.stops[0].status").value("CURRENT"))
                .andExpect(jsonPath("$.result.stops[1].status").value("LOCKED"));
        configure(List.of(stop(next, "PUBLISHED"), stop(first, "PUBLISHED"))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(jsonPath("$.result.stops[1].status").value("COMPLETED"));
        configure(List.of(stop(first, "PUBLISHED"))).andExpect(status().isOk());
        mvc.perform(get("/api/users/progress").with(user(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.journeyProgress").value(100)).andExpect(jsonPath("$.result.journeyCity").value("Đã hoàn thành hành trình"));
    }
    @Test void unpublishedRequiredContentCannotBeSilentlySkippedForAStamp() throws Exception {
        long id = destination("NotReady", VietnamRegion.CENTRAL, DestinationStatus.PUBLISHED);
        long course = course("PUBLISHED"), a = lesson(course, 1, true), draft = lesson(course, 2, false);
        configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
        content(id, List.of(a, draft), List.of()).andExpect(status().isOk());
        learned(course, a, learner);
        mvc.perform(get("/api/me/journey/destinations/" + id + "/content").with(user(learner)))
                .andExpect(jsonPath("$.result.lessons.length()").value(1)).andExpect(jsonPath("$.result.journey.requiredLessons").value(2))
                .andExpect(jsonPath("$.result.journey.canComplete").value(false));
        complete(id, learner).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passport_entries WHERE user_id=?", Integer.class, learner.id())).isZero();
        // A legitimate earlier completion remains valid even after its lesson is unpublished.
        learned(course, draft, learner);
        complete(id, learner).andExpect(status().isOk());
        mvc.perform(put("/api/admin/journey").with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("stops", List.of(stop(id, "PUBLISHED")), "unlockRule", "NONE"))))
                .andExpect(status().isBadRequest());
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentCompletionRetriesIssueExactlyOneProgressAndPassport() throws Exception {
        Long id = null, course = null, lesson = null;
        try {
            id = destination("Concurrent", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
            course = course("PUBLISHED"); lesson = lesson(course, 1, true);
            configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
            content(id, List.of(lesson), List.of()).andExpect(status().isOk()); learned(course, lesson, learner);
            long destination = id;
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<MvcResult> call = () -> complete(destination, learner).andExpect(status().isOk()).andReturn();
                for (var response : pool.invokeAll(List.of(call, call), 15, TimeUnit.SECONDS))
                    assertThat(result(response.get()).path("completedStops").asInt()).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_journey_progress WHERE user_id=?", Integer.class, learner.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passport_entries WHERE user_id=?", Integer.class, learner.id())).isEqualTo(1);
        } finally { cleanup(id, course, lesson); }
    }
    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedStampInsertRollsBackCompletionAndKeepsStartedProgress() throws Exception {
        Long id = null, course = null, lesson = null;
        try {
            id = destination("Rollback", VietnamRegion.NORTH, DestinationStatus.PUBLISHED);
            course = course("PUBLISHED"); lesson = lesson(course, 1, true);
            configure(List.of(stop(id, "PUBLISHED"))).andExpect(status().isOk());
            content(id, List.of(lesson), List.of()).andExpect(status().isOk());
            start(id, learner).andExpect(status().isOk()); learned(course, lesson, learner);
            doThrow(new org.springframework.dao.DataIntegrityViolationException("test stamp insertion failure"))
                    .when(stamps).saveAndFlush(any(PassportEntryJpaEntity.class));
            complete(id, learner).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT status FROM user_journey_progress WHERE user_id=? AND destination_id=?", String.class, learner.id(), id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT completed_at FROM user_journey_progress WHERE user_id=? AND destination_id=?", java.sql.Timestamp.class, learner.id(), id)).isNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_passport_entries WHERE user_id=?", Integer.class, learner.id())).isZero();
        } finally { cleanup(id, course, lesson); }
    }
    private void cleanup(Long destination, Long course, Long lesson) {
        // Only these committed fixtures in the isolated test database.
        if (destination != null) jdbc.update("DELETE FROM destinations WHERE id=?", destination);
        if (lesson != null) jdbc.update("DELETE FROM lesson_progress WHERE lesson_id=?", lesson);
        if (course != null) {
            jdbc.update("DELETE FROM enrollments WHERE course_id=?", course);
            jdbc.update("DELETE FROM lessons WHERE course_id=?", course);
            jdbc.update("DELETE FROM courses WHERE id=?", course);
        }
        for (var principal : List.of(learner, other, admin)) jdbc.update("DELETE FROM users WHERE id=?", principal.id());
    }
}
