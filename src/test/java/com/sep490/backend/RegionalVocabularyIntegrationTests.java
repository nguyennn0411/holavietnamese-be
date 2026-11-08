package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.common.security.LearnerPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class RegionalVocabularyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private final LearnerPrincipal admin = new LearnerPrincipal(830001L,"regional-manager@example.test","",true,
            "CONTENT",Set.of("CONTENT"),Set.of("VOCABULARY_READ","VOCABULARY_WRITE","VOCABULARY_PUBLISH"));
    private final LearnerPrincipal learner = new LearnerPrincipal(830002L,"regional-learner@example.test","",true);
    private long master;
    @BeforeEach void fixture() {
        for (var user : List.of(admin, learner)) jdbc.update("INSERT INTO users(id,username,email,password_hash) VALUES(?,?,?,'unused')",user.id(),user.email(),user.email());
        jdbc.update("INSERT INTO vocabularies(word,part_of_speech,cefr_level,status) VALUES('Quả dứa','noun','A1','PUBLISHED')");
        master = jdbc.queryForObject("SELECT MAX(id) FROM vocabularies",Long.class);
    }
    private Map<String,Object> data(String word, String region, String status) {
        return new LinkedHashMap<>(Map.of("vocabularyId",master,"word",word,"region",region,"meaning","Pineapple",
                "exampleSentence","Tôi mua một quả dứa.","usageNote","Ví dụ kiểm thử","status",status));
    }
    private ResultActions create(Map<String,Object> input, LearnerPrincipal actor) throws Exception {
        return mvc.perform(post("/api/admin/regional-language").with(user(actor)).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(input)));
    }
    private long publish(String word,String region) throws Exception {
        long id=json.readTree(create(data(word,region,"DRAFT"),admin).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString()).path("result").path("id").asLong();
        mvc.perform(post("/api/admin/regional-language/"+id+"/submit-review").with(user(admin)).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/regional-language/"+id+"/review").with(user(admin)).with(csrf())
                .contentType("application/json").content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk());
        return id;
    }
    @Test void adminCreatesEditsArchivesAndComparisonOnlyShowsPublishedMasterAndVariants() throws Exception {
        long id = publish("Thơm","SOUTH");
        publish("Dứa","NORTH");
        create(data("Khóm","SOUTH","DRAFT"),admin).andExpect(status().isCreated());
        mvc.perform(get("/api/regional-vietnamese").param("keyword","thơm").param("region","SOUTH").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].variants.length()").value(2));
        mvc.perform(get("/api/regional-vietnamese/"+master).with(user(learner))).andExpect(jsonPath("$.result.word").value("Quả dứa"));
        mvc.perform(post("/api/admin/regional-language/"+id+"/archive").with(user(admin)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/regional-vietnamese").param("region","SOUTH").with(user(learner))).andExpect(jsonPath("$.result.length()").value(0));
        jdbc.update("UPDATE vocabularies SET status='DRAFT' WHERE id=?", master);
        mvc.perform(get("/api/regional-vietnamese/"+master).with(user(learner))).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidRegionMissingMasterBlankAndDuplicatesAndEnforcesAdmin() throws Exception {
        create(data("Thơm","SOUTH","DRAFT"),learner).andExpect(status().isForbidden());
        create(data("Thơm","SOUTH","DRAFT"),new LearnerPrincipal(839901L,"system-admin@example.test","",true,"ADMIN"))
                .andExpect(status().isForbidden());
        create(data("Thơm","WEST","DRAFT"),admin).andExpect(status().isBadRequest());
        var input = data("Thơm","SOUTH","DRAFT"); input.put("vocabularyId",999999L);
        create(input,admin).andExpect(status().isNotFound());
        create(data(" ","SOUTH","DRAFT"),admin).andExpect(status().isBadRequest());
        create(data(" Thơm ","SOUTH","DRAFT"),admin).andExpect(status().isCreated());
        create(data("thơm","SOUTH","DRAFT"),admin).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/regional-language").with(user(learner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/regional-vietnamese")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regional_vocabularies",Integer.class)).isEqualTo(1);
    }
    @Test void destinationRetainsMasterLinksAndIncludesOnlyVariantsOfItsRegion() throws Exception {
        publish("Thơm","SOUTH");
        publish("Dứa","NORTH");
        jdbc.update("INSERT INTO destinations(name_vi,name_en,slug,region,status) VALUES('Test','Test','regional-test','SOUTH','PUBLISHED')");
        long destination = jdbc.queryForObject("SELECT MAX(id) FROM destinations",Long.class);
        mvc.perform(put("/api/admin/destinations/"+destination+"/explore-content").with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("cultureIds",List.of(),"scenarioIds",List.of(),"vocabularyIds",List.of(master)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.regionalVariants[0].word").value("Thơm"));
        mvc.perform(get("/api/destinations/"+destination+"/content").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.vocabulary[0].id").value(master))
                .andExpect(jsonPath("$.result.regionalVariants.length()").value(1))
                .andExpect(jsonPath("$.result.regionalVariants[0].region").value("SOUTH"));
    }
    @Test void adminListUsesBoundedServerSidePagination() throws Exception {
        for(int index=0;index<25;index++) create(data("Biến thể "+index,"SOUTH","DRAFT"),admin).andExpect(status().isCreated());
        mvc.perform(get("/api/admin/regional-language").param("page","1").param("size","10").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content.length()").value(10))
                .andExpect(jsonPath("$.result.number").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(25))
                .andExpect(jsonPath("$.result.totalPages").value(3));
        mvc.perform(get("/api/admin/regional-language").param("page","0").param("size","500").with(user(admin)))
                .andExpect(jsonPath("$.result.size").value(100));
    }

    @Test
    void reviewerCanApproveEveryPendingRegionalVariant() throws Exception {
        for (String word : List.of("Chờ duyệt một", "Chờ duyệt hai")) {
            long id = json.readTree(create(data(word,"SOUTH","DRAFT"),admin)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                    .path("result").path("id").asLong();
            mvc.perform(post("/api/admin/regional-language/"+id+"/submit-review")
                            .with(user(admin)).with(csrf()))
                    .andExpect(status().isOk());
        }
        create(data("Giữ nguyên bản nháp","NORTH","DRAFT"),admin).andExpect(status().isCreated());

        mvc.perform(post("/api/admin/regional-language/review/approve-all")
                        .with(user(learner)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/regional-language/review/approve-all")
                        .with(user(admin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM regional_vocabularies WHERE vocabulary_id=? AND status='PENDING_REVIEW'",
                Integer.class, master)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM regional_vocabularies WHERE vocabulary_id=? AND status='PUBLISHED'",
                Integer.class, master)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM regional_vocabularies WHERE vocabulary_id=? AND status='DRAFT'",
                Integer.class, master)).isEqualTo(1);
    }
}
