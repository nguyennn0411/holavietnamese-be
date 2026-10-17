package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
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
    private final LearnerPrincipal admin = new LearnerPrincipal(830001L,"regional-admin@example.test","",true,"ADMIN");
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
    @Test void adminCreatesEditsArchivesAndComparisonOnlyShowsPublishedMasterAndVariants() throws Exception {
        var node = json.readTree(create(data("Thơm","SOUTH","PUBLISHED"),admin).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("result");
        long id = node.path("id").asLong();
        create(data("Dứa","NORTH","PUBLISHED"),admin).andExpect(status().isCreated());
        create(data("Khóm","SOUTH","DRAFT"),admin).andExpect(status().isCreated());
        mvc.perform(get("/api/regional-vietnamese").param("keyword","thơm").param("region","SOUTH").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].variants.length()").value(2));
        mvc.perform(get("/api/regional-vietnamese/"+master).with(user(learner))).andExpect(jsonPath("$.result.word").value("Quả dứa"));
        mvc.perform(put("/api/admin/regional-language/"+id).with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsBytes(data("Thơm","SOUTH","ARCHIVED")))).andExpect(status().isOk());
        mvc.perform(get("/api/regional-vietnamese").param("region","SOUTH").with(user(learner))).andExpect(jsonPath("$.result.length()").value(0));
        jdbc.update("UPDATE vocabularies SET status='DRAFT' WHERE id=?", master);
        mvc.perform(get("/api/regional-vietnamese/"+master).with(user(learner))).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidRegionMissingMasterBlankAndDuplicatesAndEnforcesAdmin() throws Exception {
        create(data("Thơm","SOUTH","DRAFT"),learner).andExpect(status().isForbidden());
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
        create(data("Thơm","SOUTH","PUBLISHED"),admin).andExpect(status().isCreated());
        create(data("Dứa","NORTH","PUBLISHED"),admin).andExpect(status().isCreated());
        jdbc.update("INSERT INTO destinations(name_vi,name_en,slug,region,status) VALUES('Test','Test','regional-test','SOUTH','PUBLISHED')");
        long destination = jdbc.queryForObject("SELECT MAX(id) FROM destinations",Long.class);
        mvc.perform(put("/api/admin/destinations/"+destination+"/content").with(user(admin)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("lessonIds",List.of(),"vocabularyIds",List.of(master)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.regionalVariants[0].word").value("Thơm"));
        mvc.perform(get("/api/me/journey/destinations/"+destination+"/content").with(user(learner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.vocabulary[0].id").value(master))
                .andExpect(jsonPath("$.result.regionalVariants.length()").value(1))
                .andExpect(jsonPath("$.result.regionalVariants[0].region").value("SOUTH"));
    }
}
