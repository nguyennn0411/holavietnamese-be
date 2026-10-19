package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.importing.DictionarySources;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class DictionaryIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DictionarySources sources;
    LearnerPrincipal admin,learner;
    @BeforeEach void fixture() { admin=principal("ADMIN"); learner=principal("LEARNER"); }
    LearnerPrincipal principal(String role) {
        String name="dict-"+UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO users(username,email,password_hash) VALUES(?,?,'unused')",name,name);
        return new LearnerPrincipal(jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name),name,"",true,role);
    }
    JsonNode result(ResultActions call) throws Exception { return json.readTree(call.andReturn().getResponse().getContentAsString()).path("result"); }
    ResultActions preview(byte[] input,LearnerPrincipal actor) throws Exception {
        return mvc.perform(multipart("/api/admin/imports/preview").file(new MockMultipartFile("file","pilot.jsonl","application/x-ndjson",input))
                .param("module","DICTIONARY_JSONL").with(user(actor)).with(csrf()));
    }
    ResultActions confirm(String id) throws Exception { return mvc.perform(post("/api/admin/imports/"+id+"/confirm").with(user(admin)).with(csrf())); }
    String request(String word,String part,String state,String vi,String en) throws Exception {
        var meaning=new LinkedHashMap<String,Object>(); meaning.put("definitionVi",vi); meaning.put("translationEn",en);
        meaning.put("examples",List.of(Map.of("exampleVi","Một câu có "+word+".")));
        return json.writeValueAsString(Map.of("word",word,"partOfSpeech",part,"status",state,"meanings",List.of(meaning)));
    }
    long create(String word,String part,String state,String vi,String en) throws Exception {
        return result(mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json")
                .content(request(word,part,state,vi,en))).andExpect(status().isCreated())).path("id").asLong();
    }

    @Test void realPilotPreviewImportRepeatAndAdminEditsPreserveMasterNotebookAndReview() throws Exception {
        byte[] pilot; try(var input=sources.pilot().getInputStream()) { pilot=input.readAllBytes(); }
        long original=jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies",Long.class);
        var job=result(preview(pilot,admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.canConfirm").value(true)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies",Long.class)).isEqualTo(original);
        String jobId=job.path("id").asText();
        confirm(jobId).andExpect(status().isOk()).andExpect(jsonPath("$.result.importedRows").value(200)).andExpect(jsonPath("$.result.success").value(true));
        confirm(jobId).andExpect(status().isOk()).andExpect(jsonPath("$.result.importedRows").value(200));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE source_metadata IS NOT NULL AND status='PENDING_REVIEW'",Long.class)).isEqualTo(200);
        var pending=result(mvc.perform(get("/api/admin/vocabulary/search").param("keyword","bàn").param("exact","true").with(user(admin))).andExpect(status().isOk()));
        long id=pending.path("content").get(0).path("id").asLong();
        mvc.perform(get("/api/vocabulary/"+id).with(user(learner))).andExpect(status().isNotFound());
        String part=pending.path("content").get(0).path("partOfSpeech").asText();
        var edited=result(mvc.perform(put("/api/admin/vocabulary/"+id).with(user(admin)).with(csrf()).contentType("application/json")
                .content(request("bàn",part,"PUBLISHED","Định nghĩa đã được Admin biên tập.",null))).andExpect(status().isOk()));
        assertThat(edited.path("source").path("edited").asBoolean()).isTrue();
        var publicEntry=result(mvc.perform(get("/api/vocabulary/"+id).with(user(learner))).andExpect(status().isOk()));
        assertThat(publicEntry.has("sourceRaw")).isFalse();
        assertThat(publicEntry.toString()).doesNotContain("_original_record_sha256","_dictionary");
        assertThat(publicEntry.path("source").path("license").asText()).isEqualTo("CC-BY-SA-4.0");
        var saved=json.readTree(mvc.perform(post("/api/me/vocabulary").with(user(learner)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("masterVocabularyId",id,"word","bàn","meaning","Định nghĩa đã được Admin biên tập.","note","Ghi chú cá nhân"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long notebook=saved.path("id").asLong();
        mvc.perform(post("/api/me/vocabulary/"+notebook+"/review").with(user(learner)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sessionId",UUID.randomUUID(),"rating","GOOD")))).andExpect(status().isOk());
        var reviewBefore=jdbc.queryForMap("SELECT * FROM vocabulary_review_states WHERE vocabulary_entry_id=?",notebook);
        String rawBefore=jdbc.queryForObject("SELECT source_raw FROM vocabularies WHERE id=?",String.class,id);
        mvc.perform(put("/api/admin/vocabulary/"+id).with(user(admin)).with(csrf()).contentType("application/json")
                .content(request("bàn (đã biên tập)",part,"PUBLISHED","Định nghĩa đã được Admin biên tập.",null))).andExpect(status().isOk());
        var repeat=result(preview(pilot,admin).andExpect(jsonPath("$.result.summary.skippedExisting").value(200)));
        confirm(repeat.path("id").asText()).andExpect(status().isOk()).andExpect(jsonPath("$.result.importedRows").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies",Long.class)).isEqualTo(original+200);
        assertThat(jdbc.queryForObject("SELECT definition_vi FROM vocabulary_meanings WHERE vocabulary_id=?",String.class,id)).isEqualTo("Định nghĩa đã được Admin biên tập.");
        assertThat(jdbc.queryForObject("SELECT word FROM vocabularies WHERE id=?",String.class,id)).isEqualTo("bàn (đã biên tập)");
        assertThat(jdbc.queryForObject("SELECT source_raw FROM vocabularies WHERE id=?",String.class,id)).isEqualTo(rawBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM vocabulary_review_states WHERE vocabulary_entry_id=?",notebook)).isEqualTo(reviewBefore);
        assertThat(jdbc.queryForObject("SELECT note FROM vocabulary_entries WHERE id=?",String.class,notebook)).isEqualTo("Ghi chú cá nhân");
        mvc.perform(get("/api/admin/imports/"+repeat.path("id").asText()).with(user(admin))).andExpect(jsonPath("$.result.summary.skippedExisting").value(200));
    }

    @Test void databasePaginationExactAccentlessAndMeaningSearchRespectPublicationAndPos() throws Exception {
        long table=create("bàn","noun","PUBLISHED","Đồ vật có mặt phẳng.","table");
        create("bàn","verb","PUBLISHED","Trao đổi ý kiến.","discuss");
        create("bàn học","noun","PUBLISHED","Bàn dùng để học.","desk");
        create("bán","verb","PUBLISHED","Chuyển quyền sở hữu.","sell");
        long hidden=create("bàn bí mật","noun","PENDING_REVIEW","Ẩn.",null);
        mvc.perform(get("/api/vocabulary/search").with(user(learner)).param("keyword","ban").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(4)).andExpect(jsonPath("$.result.totalPages").value(4))
                .andExpect(jsonPath("$.result.content.length()").value(1));
        mvc.perform(get("/api/vocabulary/search").with(user(learner)).param("keyword","bàn"))
                .andExpect(jsonPath("$.result.content[0].id").value(table));
        mvc.perform(get("/api/vocabulary/search").with(user(learner)).param("keyword","bàn").param("exact","true").param("partOfSpeech","noun"))
                .andExpect(jsonPath("$.result.totalElements").value(1)).andExpect(jsonPath("$.result.content[0].id").value(table));
        for(String keyword:List.of("Trao đổi","discuss")) mvc.perform(get("/api/vocabulary/search").with(user(learner)).param("keyword",keyword))
                .andExpect(jsonPath("$.result.totalElements").value(1));
        mvc.perform(get("/api/vocabulary/"+table+"/links").with(user(learner))).andExpect(jsonPath("$.result.otherPartsOfSpeech.length()").value(1));
        mvc.perform(get("/api/vocabulary/"+hidden+"/links").with(user(learner))).andExpect(status().isNotFound());
        mvc.perform(get("/api/vocabulary/search").with(user(learner)).param("keyword","%"))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mvc.perform(get("/api/vocabulary/filters").with(user(learner))).andExpect(jsonPath("$.result.levels").isEmpty());
        mvc.perform(get("/api/vocabulary/search")).andExpect(status().isUnauthorized());
    }

    @Test void missingMeaningInvalidCefrDuplicatesAndUnverifiedSourceCannotBeImported() throws Exception {
        create("nước","noun","DRAFT","Chất lỏng.",null);
        mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json")
                .content(request("NƯỚC","NOUN","DRAFT","Khác.",null))).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json")
                .content(request("từ lỗi","noun","DRAFT",null,null))).andExpect(status().isBadRequest());
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(request("CEFR lỗi","noun","DRAFT","Từ.",null)); payload.put("cefrLevel","Z9");
        mvc.perform(post("/api/admin/vocabulary").with(user(admin)).with(csrf()).contentType("application/json").content(payload.toString())).andExpect(status().isBadRequest());
        byte[] unverified="{\"word\":\"mẫu\",\"lang_code\":\"vi\",\"pos\":\"noun\",\"senses\":[{\"glosses\":[\"Mẫu.\"]}]}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var job=result(preview(unverified,admin).andExpect(jsonPath("$.result.canConfirm").value(false)));
        confirm(job.path("id").asText()).andExpect(status().isConflict());
        preview(unverified,learner).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports/template").param("module","DICTIONARY_JSONL").with(user(admin))).andExpect(status().isOk());
    }

    @Test void migrationBackfillPreservesExistingTextAndEditorialTimestamp() throws Exception {
        long id=create("ĐƯỜNG bảo toàn","noun","DRAFT","Định nghĩa cũ.",null);
        var original=java.sql.Timestamp.valueOf("2020-01-02 03:04:05");
        jdbc.update("UPDATE vocabularies SET updated_at=?,search_word=NULL WHERE id=?",original,id);
        String sql=new org.springframework.core.io.ClassPathResource("db/migration/V20__dictionary_content_and_provenance.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("UPDATE vocabularies")).findFirst().orElseThrow();
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM vocabularies WHERE id=?",java.sql.Timestamp.class,id)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT word FROM vocabularies WHERE id=?",String.class,id)).isEqualTo("ĐƯỜNG bảo toàn");
        assertThat(jdbc.queryForObject("SELECT search_word FROM vocabularies WHERE id=?",String.class,id)).isEqualTo("duong bao toan");
        assertThat(jdbc.queryForObject("SELECT definition_vi FROM vocabulary_meanings WHERE vocabulary_id=?",String.class,id)).isEqualTo("Định nghĩa cũ.");
    }
}
