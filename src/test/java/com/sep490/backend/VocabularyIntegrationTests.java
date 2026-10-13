package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class VocabularyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean Clock clock;
    private final LearnerPrincipal learner = new LearnerPrincipal(810001L, "vocabulary-one@example.test", "", true);
    private final LearnerPrincipal other = new LearnerPrincipal(810002L, "vocabulary-two@example.test", "", true);
    private final LearnerPrincipal admin = new LearnerPrincipal(810003L, "vocabulary-admin@example.test", "", true, "ADMIN");
    private final Instant now = Instant.parse("2026-10-13T18:00:00Z");

    @BeforeEach void fixture() {
        when(clock.instant()).thenReturn(now);
        for (var p : List.of(learner, other, admin)) {
            jdbc.update("insert into users(id,username,email,password_hash) values(?,?,?,?)", p.id(), p.email(), p.email(), "unused-test-hash");
        }
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private String word(String status, Long topicId) throws Exception {
        return json.writeValueAsString(Map.of("word", "tạm biệt", "partOfSpeech", "PHRASE", "cefrLevel", "A1",
                "pronunciation", "/tam biet/", "audioUrl", "/audio/goodbye.mp3", "status", status,
                "topicIds", topicId == null ? List.of() : List.of(topicId), "meanings", List.of(
                        Map.of("translationEn", "goodbye", "usageNote", "Southern and Northern greeting",
                                "examples", List.of(Map.of("exampleVi", "Tạm biệt!", "translationEn", "Goodbye!"))),
                        Map.of("translationEn", "farewell"))));
    }
    private long master(String status) throws Exception {
        return body(mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf())
                .contentType("application/json").content(word(status, null))).andExpect(status().isCreated()).andReturn()).path("result").path("id").asLong();
    }
    private String notebook(Long master, String word, String meaning, String note) throws Exception {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("masterVocabularyId", master); payload.put("word", word); payload.put("meaning", meaning); payload.put("note", note);
        return json.writeValueAsString(payload);
    }
    private long save(Long master) throws Exception {
        return body(mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json")
                .content(notebook(master, "tạm biệt", "goodbye", "note"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.review.memoryStatus").value("NEW")).andReturn()).path("id").asLong();
    }
    private ResultActions rate(long entry, UUID session, String rating, LearnerPrincipal principal) throws Exception {
        return mvc.perform(post("/api/me/vocabulary/" + entry + "/review").with(user(principal)).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(Map.of("sessionId", session, "rating", rating))));
    }

    @Test void catalogSearchesBothLanguagesCombinesFiltersAndProtectsUnpublishedContent() throws Exception {
        var topic = body(mvc.perform(post("/api/admin/vocabulary-topics").with(user(admin)).with(csrf())
                .contentType("application/json").content("{\"name\":\"Greetings\",\"slug\":\"vocabulary-test-greetings\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isCreated()).andReturn()).path("result").path("id").asLong();
        var published = body(mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json")
                .content(word("PUBLISHED", topic))).andExpect(status().isCreated()).andReturn()).path("result").path("id").asLong();
        for (String state : List.of("DRAFT", "PENDING_REVIEW", "ARCHIVED")) {
            long hidden = master(state);
            mvc.perform(get("/api/vocabulary/" + hidden).with(user(learner))).andExpect(status().isNotFound());
        }
        for (String query : List.of("tạm", "GOODBYE", "farewell")) {
            mvc.perform(get("/api/vocabulary").with(user(learner)).param("keyword", query).param("cefrLevel", "a1")
                    .param("partOfSpeech", "phrase").param("topicId", String.valueOf(topic)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                    .andExpect(jsonPath("$.result[0].id").value(published)).andExpect(jsonPath("$.result[0].meanings.length()").value(2));
        }
        mvc.perform(get("/api/vocabulary").with(user(learner)).param("keyword", "%")).andExpect(jsonPath("$.result.length()").value(0));
        mvc.perform(get("/api/vocabulary").with(user(learner)).param("cefrLevel", "C2")).andExpect(jsonPath("$.result.length()").value(0));
        mvc.perform(put("/api/admin/vocabulary/" + published).with(user(admin)).with(csrf()).contentType("application/json").content(word("ARCHIVED", topic)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.meanings.length()").value(2));
        mvc.perform(get("/api/vocabulary/" + published).with(user(learner))).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/vocabulary-topics/" + topic).with(user(admin)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Greetings edited\",\"slug\":\"vocabulary-test-greetings\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.name").value("Greetings edited"));
    }

    @Test void notebookValidatesMasterAndPreservesLinkAndOwnerOnNoteUpdates() throws Exception {
        long master = master("PUBLISHED"), entry = save(master);
        mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json")
                .content(notebook(master, "tạm biệt", "goodbye", ""))).andExpect(status().isConflict());
        for (long hidden : List.of(99999999L, master("DRAFT"), master("PENDING_REVIEW"), master("ARCHIVED"))) {
            mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json")
                    .content(notebook(hidden, "tạm biệt", "goodbye", ""))).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/me/vocabulary").with(user(other)).with(csrf()).contentType("application/json")
                .content(notebook(master, "wrong", "goodbye", ""))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/vocabulary/" + entry).with(user(learner)).with(csrf()).contentType("application/json")
                .content(notebook(null, "tạm biệt", "goodbye", "updated"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.masterVocabularyId").value(master)).andExpect(jsonPath("$.note").value("updated"))
                .andExpect(jsonPath("$.audioUrl").value("/audio/goodbye.mp3"));
        mvc.perform(put("/api/me/vocabulary/" + entry).with(user(learner)).with(csrf()).contentType("application/json")
                .content(notebook(master, "wrong", "goodbye", ""))).andExpect(status().isBadRequest());
        for (var principal : List.of(other, admin)) {
            mvc.perform(get("/api/me/vocabulary/" + entry).with(user(principal))).andExpect(status().isNotFound());
            mvc.perform(delete("/api/me/vocabulary/" + entry).with(user(principal)).with(csrf())).andExpect(status().isNotFound());
        }
        long manual = save(null);
        mvc.perform(put("/api/me/vocabulary/" + manual).with(user(learner)).with(csrf()).contentType("application/json")
                .content(notebook(null, "new word", "new meaning", "updated"))).andExpect(status().isOk()).andExpect(jsonPath("$.word").value("new word"));
    }

    @Test void reviewSchedulesFourRatingsPersistsResultsResumesAndCascadesDelete() throws Exception {
        var session = UUID.fromString(body(mvc.perform(get("/api/me/vocabulary/review").with(user(learner)))
                .andExpect(status().isOk()).andReturn()).path("sessionId").asText());
        var ids = new ArrayList<Long>();
        var ratings = List.of("AGAIN", "HARD", "GOOD", "EASY");
        var intervals = List.of(10L, 720L, 1440L, 5760L);
        for (int i = 0; i < ratings.size(); i++) {
            long entry = save(null); ids.add(entry);
            rate(entry, session, ratings.get(i), learner).andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value(ratings.get(i))).andExpect(jsonPath("$.intervalMinutes").value(intervals.get(i)))
                    .andExpect(jsonPath("$.nextReviewAt").value(now.plusSeconds(intervals.get(i) * 60).toString()));
        }
        rate(ids.get(0), session, "AGAIN", learner).andExpect(status().isOk());
        rate(ids.get(0), session, "GOOD", learner).andExpect(status().isConflict());
        rate(ids.get(1), UUID.randomUUID(), "GOOD", learner).andExpect(status().isConflict());
        mvc.perform(get("/api/me/vocabulary/review").with(user(learner)).param("sessionId", session.toString()))
                .andExpect(jsonPath("$.entries.length()").value(0)).andExpect(jsonPath("$.results.length()").value(4));
        mvc.perform(get("/api/me/vocabulary/review").with(user(other)).param("sessionId", session.toString()))
                .andExpect(jsonPath("$.entries.length()").value(0)).andExpect(jsonPath("$.results.length()").value(0));
        when(clock.instant()).thenReturn(now.plusSeconds(601));
        mvc.perform(get("/api/me/vocabulary/review").with(user(learner))).andExpect(jsonPath("$.entries.length()").value(1));
        mvc.perform(get("/api/me/vocabulary/" + ids.get(0)).with(user(learner)))
                .andExpect(jsonPath("$.review.memoryStatus").value("LEARNING")).andExpect(jsonPath("$.review.due").value(true));
        rate(ids.get(0), UUID.randomUUID(), "GOOD", learner).andExpect(status().isOk());
        when(clock.instant()).thenReturn(now.plusSeconds(1440 * 60 + 602));
        rate(ids.get(0), UUID.randomUUID(), "GOOD", learner).andExpect(status().isOk()).andExpect(jsonPath("$.intervalMinutes").value(3312));
        mvc.perform(delete("/api/me/vocabulary/" + ids.get(0)).with(user(learner)).with(csrf())).andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from vocabulary_review_states where vocabulary_entry_id=?", Integer.class, ids.get(0))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from vocabulary_review_logs where vocabulary_entry_id=?", Integer.class, ids.get(0))).isZero();
    }

    @Test void reviewAndAdminEnforceAuthenticationCsrfOwnershipAndInputValidation() throws Exception {
        long entry = save(null);
        mvc.perform(get("/api/me/vocabulary/review")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/vocabulary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/vocabulary").with(user(learner))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/vocabulary-topics").with(user(learner)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        rate(entry, UUID.randomUUID(), "GOOD", other).andExpect(status().isNotFound());
        rate(entry, UUID.randomUUID(), "GOOD", admin).andExpect(status().isNotFound());
        rate(entry, UUID.randomUUID(), "INVALID", learner).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/vocabulary/" + entry + "/review").with(user(learner)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/vocabulary/" + entry + "/review").with(user(learner)).contentType("application/json")
                .content("{\"sessionId\":\"" + UUID.randomUUID() + "\",\"rating\":\"GOOD\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/vocabulary/review").with(user(learner)).param("sessionId", "bad-id")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json")
                .content(word("PUBLISHED", null).replace("\"meanings\":[", "\"meanings\":[null,"))).andExpect(status().isBadRequest());
    }

    @Test @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRatingRetriesWriteOnlyOneStateAndLog() throws Exception {
        Long entry = null;
        try {
            entry = save(null);
            long id = entry;
            UUID session = UUID.randomUUID();
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<MvcResult> request = () -> rate(id, session, "GOOD", learner).andExpect(status().isOk()).andReturn();
                var requests = pool.invokeAll(List.of(request, request), 15, TimeUnit.SECONDS);
                for (var result : requests) assertThat(body(result.get()).path("intervalMinutes").asLong()).isEqualTo(1440);
            }
            assertThat(jdbc.queryForObject("select count(*) from vocabulary_review_logs where vocabulary_entry_id=?", Integer.class, entry)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from vocabulary_review_states where vocabulary_entry_id=?", Integer.class, entry)).isEqualTo(1);
            mvc.perform(delete("/api/me/vocabulary/" + entry).with(user(learner)).with(csrf())).andExpect(status().isNoContent());
            assertThat(jdbc.queryForObject("select count(*) from vocabulary_review_logs where vocabulary_entry_id=?", Integer.class, entry)).isZero();
        } finally {
            // Only this test's committed fixtures, in the isolated test database.
            if (entry != null) jdbc.update("delete from vocabulary_entries where id=? and user_id=?", entry, learner.id());
            for (var principal : List.of(learner, other, admin)) jdbc.update("delete from users where id=?", principal.id());
        }
    }
}
