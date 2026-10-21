package com.sep490.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.request.DestinationRequest;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.service.DestinationService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class DestinationExploreIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired DestinationService destinations;
    @Autowired jakarta.persistence.EntityManager em;
    LearnerPrincipal learner, admin;
    @BeforeEach void fixture() { learner=principal("LEARNER"); admin=principal("ADMIN"); }
    LearnerPrincipal principal(String role) {
        String email=UUID.randomUUID()+"@explore.test";
        db.update("INSERT INTO users(username,email,password_hash) VALUES(?,?,'test')",email,email);
        long id=db.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        return new LearnerPrincipal(id,email,"",true,role);
    }
    long destination(DestinationStatus status) {
        String slug="explore-"+UUID.randomUUID();
        return destinations.create(new DestinationRequest("Huế", "Hue",slug,VietnamRegion.CENTRAL,null,null,null,null,null,status)).id();
    }
    long word(String status) {
        db.update("INSERT INTO vocabularies(word,part_of_speech,status) VALUES(?,'noun',?)","word-"+UUID.randomUUID(),status);
        return db.queryForObject("SELECT MAX(id) FROM vocabularies",Long.class);
    }
    String scenario(String status) {
        String id="explore-"+UUID.randomUUID();
        db.update("INSERT INTO ai_scenarios(id,title,ai_role_name,ai_role_initial_greeting,status) VALUES(?,'Gọi món','Người bán','Xin chào',?)",id,status);
        return id;
    }
    ResultActions save(long id,List<Long> words,List<String> cultures,List<String> scenarios,LearnerPrincipal actor) throws Exception {
        return mvc.perform(put("/api/admin/destinations/"+id+"/explore-content").with(user(actor)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("vocabularyIds",words,"cultureIds",cultures,"scenarioIds",scenarios))));
    }
    @Test void anyPublishedDestinationOpensWithoutLearningAndReadsDoNotWriteProgress() throws Exception {
        long first=destination(DestinationStatus.PUBLISHED), next=destination(DestinationStatus.PUBLISHED);
        db.update("INSERT INTO journey_stops(destination_id,display_order,status) VALUES(?,1,'PUBLISHED'),(?,2,'PUBLISHED')",first,next);
        var progress=db.queryForList("SELECT * FROM user_journey_progress");
        var stamps=db.queryForList("SELECT * FROM user_passport_entries");
        for (int repeat=0;repeat<2;repeat++) {
            mvc.perform(get("/api/destinations/"+next).with(user(learner))).andExpect(status().isOk());
            mvc.perform(get("/api/destinations/"+next+"/content").with(user(learner))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.lessons").doesNotExist()).andExpect(jsonPath("$.result.journey").doesNotExist());
        }
        assertThat(db.queryForList("SELECT * FROM user_journey_progress")).isEqualTo(progress);
        assertThat(db.queryForList("SELECT * FROM user_passport_entries")).isEqualTo(stamps);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id=?",Integer.class,learner.id())).isZero();
    }
    @Test void referencesPersistInOrderWithoutEditingSourceAndLearnersSeeOnlyAvailableContent() throws Exception {
        long id=destination(DestinationStatus.PUBLISHED), published=word("PUBLISHED"), draft=word("DRAFT");
        String active=scenario("ACTIVE"), hidden=scenario("DRAFT");
        db.update("INSERT INTO regional_vocabularies(vocabulary_id,region,word,meaning,status) VALUES(?,'CENTRAL','mô','Ở đâu','PUBLISHED')",published);
        var sources=List.of("vocabularies","ai_scenarios","courses","lesson_progress","ai_sessions");
        var before=sources.stream().map(t -> db.queryForList("SELECT * FROM "+t+" ORDER BY 1")).toList();
        save(id,List.of(published,draft),List.of("van-hoa-hue","am-thuc-hue"),List.of(active,hidden),admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.scenarios.length()").value(2));
        em.flush(); em.clear();
        mvc.perform(get("/api/destinations/"+id+"/content").with(user(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.vocabulary.length()").value(1))
                .andExpect(jsonPath("$.result.regionalVariants[0].word").value("mô"))
                .andExpect(jsonPath("$.result.cultureIds[0]").value("van-hoa-hue"))
                .andExpect(jsonPath("$.result.scenarios.length()").value(1))
                .andExpect(jsonPath("$.result.scenarios[0].id").value(active));
        assertThat(sources.stream().map(t -> db.queryForList("SELECT * FROM "+t+" ORDER BY 1")).toList()).usingRecursiveComparison().isEqualTo(before);
        // Removing a source scenario leaves a stale reference hidden rather than breaking the page.
        db.update("DELETE FROM ai_scenarios WHERE id=?",active); em.clear();
        mvc.perform(get("/api/destinations/"+id+"/content").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.scenarios.length()").value(0));
    }
    @Test void invalidReferencesAndDuplicatesDoNotReplaceExistingLinks() throws Exception {
        long id=destination(DestinationStatus.PUBLISHED), word=word("PUBLISHED");
        save(id,List.of(word),List.of("hue"),List.of(),admin).andExpect(status().isOk());
        save(id,List.of(word,word),List.of(),List.of(),admin).andExpect(status().isBadRequest());
        save(id,List.of(),List.of(),List.of("not-found"),admin).andExpect(status().isNotFound());
        save(id,List.of(word),List.of("../bad"),List.of(),admin).andExpect(status().isBadRequest());
        save(id,List.of(),List.of(),List.of(),learner).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/destinations/"+id+"/explore-content").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.vocabulary[0].id").value(word))
                .andExpect(jsonPath("$.result.cultureIds[0]").value("hue"));
    }
    @Test void draftArchivedAndAnonymousAccessRemainProtected() throws Exception {
        for (var state:List.of(DestinationStatus.DRAFT,DestinationStatus.ARCHIVED)) {
            long id=destination(state);
            mvc.perform(get("/api/destinations/"+id).with(user(learner))).andExpect(status().isNotFound());
            mvc.perform(get("/api/destinations/"+id+"/content").with(user(learner))).andExpect(status().isNotFound());
            mvc.perform(get("/api/admin/destinations/"+id+"/explore-content").with(user(admin))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/destinations/1/content")).andExpect(status().isUnauthorized());
    }
    @Test void oldWriteEndpointsAreRetiredAndHistoricalPassportRemainsReadable() throws Exception {
        long id=destination(DestinationStatus.PUBLISHED);
        db.update("INSERT INTO user_journey_progress(user_id,destination_id,status,progress_percent) VALUES(?,?,'COMPLETED',100)",learner.id(),id);
        db.update("INSERT INTO user_passport_entries(user_id,destination_id,stamped_at) VALUES(?,?,CURRENT_TIMESTAMP)",learner.id(),id);
        var history=db.queryForList("SELECT * FROM user_passport_entries");
        for (String action:List.of("start","complete")) mvc.perform(post("/api/me/journey/destinations/"+id+"/"+action).with(user(learner)).with(csrf())).andExpect(status().isGone());
        mvc.perform(put("/api/admin/journey").with(user(admin)).with(csrf()).contentType("application/json").content("{\"stops\":[]}"))
                .andExpect(status().isGone());
        mvc.perform(get("/api/me/journey/destinations/"+id+"/content").with(user(learner))).andExpect(status().isGone());
        mvc.perform(get("/api/admin/journey").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/destinations/"+id+"/content").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey").with(user(learner))).andExpect(status().isOk());
        mvc.perform(get("/api/users/progress").with(user(learner))).andExpect(status().isOk());
        mvc.perform(get("/api/me/journey/passport").with(user(learner))).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].unlocked").value(true));
        assertThat(db.queryForList("SELECT * FROM user_passport_entries")).isEqualTo(history);
    }
}

