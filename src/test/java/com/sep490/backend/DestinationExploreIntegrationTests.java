package com.sep490.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.explore.destination.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.destination.service.DestinationService;
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
    @Autowired org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    LearnerPrincipal learner, admin;
    @BeforeEach void fixture() { learner=principal("LEARNER"); admin=principal("CONTENT_MANAGER"); }
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
    String culture(String status) {
        String id="culture-"+UUID.randomUUID();
        db.update("INSERT INTO culture_articles(id,title,status,is_featured,views) VALUES(?,'Văn hóa',?,FALSE,0)",id,status);
        return id;
    }
    ResultActions save(long id,List<Long> words,List<String> cultures,List<String> scenarios,LearnerPrincipal actor) throws Exception {
        return mvc.perform(put("/api/admin/destinations/"+id+"/explore-content").with(user(actor)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("vocabularyIds",words,"cultureIds",cultures,"scenarioIds",scenarios))));
    }
    @Test void anyPublishedDestinationOpensWithoutLearningAndReadsDoNotChangeLearning() throws Exception {
        long first=destination(DestinationStatus.PUBLISHED), next=destination(DestinationStatus.PUBLISHED);
        var sources=List.of("enrollments","lesson_progress","ai_sessions");
        var before=sources.stream().map(t -> db.queryForList("SELECT * FROM "+t+" ORDER BY 1")).toList();
        for (long id:List.of(first,next,next)) {
            mvc.perform(get("/api/destinations/"+id).with(user(learner))).andExpect(status().isOk());
            mvc.perform(get("/api/destinations/"+id+"/content").with(user(learner))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.lessons").doesNotExist()).andExpect(jsonPath("$.result.journey").doesNotExist());
        }
        assertThat(sources.stream().map(t -> db.queryForList("SELECT * FROM "+t+" ORDER BY 1")).toList()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM enrollments WHERE user_id=?",Integer.class,learner.id())).isZero();
    }
    @Test void exploreManagementUsesContentRoleOrPermissionInsteadOfAdministratorRole() throws Exception {
        mvc.perform(get("/api/admin/destinations").with(user(admin))).andExpect(status().isOk());
        var systemAdmin = new LearnerPrincipal(899910L,"system-admin@explore.test","",true,"ADMIN");
        mvc.perform(get("/api/admin/destinations").with(user(systemAdmin))).andExpect(status().isOk());
        var delegatedAdmin = new LearnerPrincipal(899911L,"read-admin@explore.test","",true,
                "ADMIN",Set.of("ADMIN"),Set.of("EXPLORE_READ"));
        mvc.perform(get("/api/admin/destinations").with(user(delegatedAdmin))).andExpect(status().isOk());
        var payload = json.writeValueAsString(Map.of("nameVi","Huế","nameEn","Hue","slug","hue-"+UUID.randomUUID(),
                "region","CENTRAL","status","DRAFT"));
        mvc.perform(post("/api/admin/destinations").with(user(admin)).with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isCreated());
        var publisher = new LearnerPrincipal(899912L,"publisher@explore.test","",true,
                "ADMIN",Set.of("ADMIN"),Set.of("EXPLORE_WRITE","EXPLORE_PUBLISH"));
        var created=json.readTree(mvc.perform(post("/api/admin/destinations").with(user(publisher)).with(csrf()).contentType("application/json")
                .content(payload.replace("hue-", "hue-admin-"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id=created.path("result").path("id").asLong();
        mvc.perform(post("/api/admin/destinations/"+id+"/submit-review").with(user(publisher)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/destinations/"+id+"/review").with(user(systemAdmin)).with(csrf())
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PUBLISHED"));
    }
    @Test void referencesPersistInOrderWithoutEditingSourceAndLearnersSeeOnlyAvailableContent() throws Exception {
        long id=destination(DestinationStatus.PUBLISHED), published=word("PUBLISHED"), draft=word("DRAFT");
        String active=scenario("ACTIVE"), hidden=scenario("DRAFT");
        String firstCulture=culture("PUBLISHED"), secondCulture=culture("PUBLISHED");
        db.update("INSERT INTO regional_vocabularies(vocabulary_id,region,word,meaning,status) VALUES(?,'CENTRAL','mô','Ở đâu','PUBLISHED')",published);
        var sources=List.of("vocabularies","ai_scenarios","courses","lesson_progress","ai_sessions");
        var before=sources.stream().map(t -> db.queryForList("SELECT * FROM "+t+" ORDER BY 1")).toList();
        save(id,List.of(published,draft),List.of(firstCulture,secondCulture),List.of(active,hidden),admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.scenarios.length()").value(2));
        em.flush(); em.clear();
        mvc.perform(get("/api/destinations/"+id+"/content").with(user(learner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.vocabulary.length()").value(1))
                .andExpect(jsonPath("$.result.regionalVariants[0].word").value("mô"))
                .andExpect(jsonPath("$.result.cultureIds[0]").value(firstCulture))
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
        String cultureId=culture("PUBLISHED");
        save(id,List.of(word),List.of(cultureId),List.of(),admin).andExpect(status().isOk());
        save(id,List.of(word,word),List.of(),List.of(),admin).andExpect(status().isBadRequest());
        save(id,List.of(),List.of(),List.of("not-found"),admin).andExpect(status().isNotFound());
        save(id,List.of(),List.of("missing-culture"),List.of(),admin).andExpect(status().isNotFound());
        save(id,List.of(),List.of(culture("DRAFT")),List.of(),admin).andExpect(status().isNotFound());
        save(id,List.of(word),List.of("../bad"),List.of(),admin).andExpect(status().isBadRequest());
        save(id,List.of(),List.of(),List.of(),learner).andExpect(status().isForbidden());
        var systemAdmin = new LearnerPrincipal(899901L,"system-admin@explore.test","",true,"ADMIN");
        save(id,List.of(),List.of(),List.of(),systemAdmin).andExpect(status().isForbidden());
        var delegatedAdmin = new LearnerPrincipal(899902L,"delegated-admin@explore.test","",true,
                "ADMIN",Set.of("ADMIN"),Set.of("EXPLORE_LINK_WRITE"));
        save(id,List.of(word),List.of(cultureId),List.of(),delegatedAdmin).andExpect(status().isOk());
        mvc.perform(get("/api/admin/destinations/"+id+"/explore-content").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.vocabulary[0].id").value(word))
                .andExpect(jsonPath("$.result.cultureIds[0]").value(cultureId));
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
    @Test
    void sharedOverviewPreservesLearningDataAndRetiredJourneyRoutesAreAbsent() throws Exception {
        mvc.perform(get("/api/users/progress").with(user(learner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enrolledCourses").isArray())
                .andExpect(jsonPath("$.result.completedLessonsCount").exists())
                .andExpect(jsonPath("$.result.journeyProgress").doesNotExist())
                .andExpect(jsonPath("$.result.journeyCity").doesNotExist());
        var routes=mappings.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
        assertThat(routes).noneMatch(path -> path.startsWith("/api/me/journey")
                || path.startsWith("/api/admin/journey") || path.equals("/api/learner/journey/progress"));
        assertThat(routes).doesNotContain("/api/admin/destinations/{id}/content");
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME IN (
                    'JOURNEY_STOPS','USER_JOURNEY_PROGRESS','USER_PASSPORT_ENTRIES','DESTINATION_LESSONS',
                    'VOCABULARY_TOPIC_MAPPINGS','VOCABULARY_TOPICS')
                """, Integer.class)).isZero();
    }
}
