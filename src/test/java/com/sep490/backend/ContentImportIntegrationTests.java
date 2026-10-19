package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.VocabularyWordService;
import com.sep490.backend.dto.request.VocabularyWordRequest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ContentImportIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean VocabularyWordService words;
    LearnerPrincipal admin,other,learner;
    @BeforeEach void fixture() {
        admin = principal("ADMIN"); other = principal("ADMIN"); learner = principal("LEARNER");
    }
    private LearnerPrincipal principal(String role) {
        String name = "import-"+UUID.randomUUID().toString().substring(0,12)+"@example.test";
        jdbc.update("INSERT INTO users(username,email,password_hash) VALUES(?,?,'unused')",name,name);
        long id = jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new LearnerPrincipal(id,name,"",true,role);
    }
    private byte[] workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("VOCABULARY");
            var header = sheet.createRow(0); var row = sheet.createRow(1);
            var columns = List.of("word","part_of_speech","cefr_level","translation_en","status","example_vi","example_en");
            var values = List.of("Từ import "+admin.id(),"noun","A1","Imported word","PUBLISHED","Một câu ví dụ.","An example sentence.");
            for(int i=0;i<columns.size();i++) { header.createCell(i).setCellValue(columns.get(i)); row.createCell(i).setCellValue(values.get(i)); }
            edit.accept(book); book.write(out); return out.toByteArray();
        }
    }
    private ResultActions preview(byte[] bytes,String filename,LearnerPrincipal actor) throws Exception {
        return mvc.perform(multipart("/api/admin/imports/preview").file(new MockMultipartFile("file",filename,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes))
                .param("module","VOCABULARY").with(user(actor)).with(csrf()));
    }
    private JsonNode data(ResultActions call) throws Exception { return json.readTree(call.andReturn().getResponse().getContentAsString()).path("result"); }
    private ResultActions confirm(String id,LearnerPrincipal actor) throws Exception {
        return mvc.perform(post("/api/admin/imports/"+id+"/confirm").with(user(actor)).with(csrf()).contentType("application/json")
                .content("{\"module\":\"USER\",\"word\":\"tampered\",\"valid\":true}"));
    }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE word=?",Long.class,"Từ import "+admin.id()); }
    @Test void previewIsReadOnlyForContentConfirmIsServerOwnedAndIdempotentWithPersistentHistory() throws Exception {
        var result = data(preview(workbook(w -> {}),"vocabulary.xlsx",admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.canConfirm").value(true)));
        assertThat(count()).isZero(); String id = result.path("id").asText();
        confirm(id,other).andExpect(status().isForbidden());
        confirm(id,admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("IMPORTED"))
                .andExpect(jsonPath("$.result.importedRows").value(1)).andExpect(jsonPath("$.result.success").value(true));
        confirm(id,admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.importedRows").value(1));
        assertThat(count()).isEqualTo(1);
        mvc.perform(get("/api/admin/imports/"+id).with(user(admin))).andExpect(jsonPath("$.result.actorId").value(admin.id()))
                .andExpect(jsonPath("$.result.completedAt").isNotEmpty()).andExpect(jsonPath("$.result.rows[0].values.word").value("Từ import "+admin.id()));
        mvc.perform(get("/api/admin/imports").with(user(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.result.content").isArray());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_examples e JOIN vocabulary_meanings m ON m.id=e.vocabulary_meaning_id JOIN vocabularies v ON v.id=m.vocabulary_id WHERE v.word=?",Integer.class,"Từ import "+admin.id())).isEqualTo(1);
    }
    @Test void invalidFilesHeadersRowsFormulaDuplicatesAndReferencesCannotBeConfirmed() throws Exception {
        for (var input : List.of(new byte[0],"not excel".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            preview(input,"invalid.xlsx",admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.canConfirm").value(false));
        preview(workbook(w -> {}),"invalid.csv",admin).andExpect(jsonPath("$.result.canConfirm").value(false));
        preview(workbook(w -> w.getSheetAt(0).getRow(0).getCell(0).setCellValue("wrong")),"missing.xlsx",admin)
                .andExpect(jsonPath("$.result.canConfirm").value(false)).andExpect(jsonPath("$.result.errors[0].row").value(1));
        var invalid = data(preview(workbook(w -> {
            var row = w.getSheetAt(0).getRow(1); row.getCell(2).setCellValue("Z9"); row.getCell(3).setCellFormula("1+1");
            w.getSheetAt(0).getRow(0).createCell(7).setCellValue("topic_ids"); row.createCell(7).setCellValue("999999");
        }),"invalid.xlsx",admin).andExpect(jsonPath("$.result.canConfirm").value(false)).andExpect(jsonPath("$.result.errorRows").value(1)));
        confirm(invalid.path("id").asText(),admin).andExpect(status().isConflict());
        preview(workbook(w -> { var row = w.getSheetAt(0).createRow(2); var original = w.getSheetAt(0).getRow(1); for(int i=0;i<7;i++) row.createCell(i).setCellValue(original.getCell(i).getStringCellValue()); }),"duplicate.xlsx",admin)
                .andExpect(jsonPath("$.result.canConfirm").value(false));
        assertThat(count()).isZero();
    }
    @Test void confirmRevalidatesDatabaseChangesSincePreview() throws Exception {
        var preview = data(preview(workbook(w -> {}),"new.xlsx",admin));
        jdbc.update("INSERT INTO vocabularies(word,part_of_speech,cefr_level,status) VALUES(?,'noun','A1','DRAFT')","Từ import "+admin.id());
        confirm(preview.path("id").asText(),admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("VALIDATION_FAILED")).andExpect(jsonPath("$.result.success").value(false));
        assertThat(count()).isEqualTo(1);
    }
    @Test void routesRequireAdminAndTemplateHasTheActualHeaderContract() throws Exception {
        preview(workbook(w -> {}),"valid.xlsx",learner).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/imports/types").with(user(admin))).andExpect(jsonPath("$.result.length()").value(2)).andExpect(jsonPath("$.result[0].id").value("VOCABULARY"));
        var file = mvc.perform(get("/api/admin/imports/template").param("module","VOCABULARY").with(user(admin))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file))) { assertThat(book.getSheet("VOCABULARY").getRow(0).getCell(0).getStringCellValue()).isEqualTo("word"); }
        mvc.perform(get("/api/admin/imports/template").param("module","USER").with(user(admin))).andExpect(status().isBadRequest());
    }
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void writeFailureRollsBackTheWholeImportAndRecordsFailure() throws Exception {
        byte[] upload = workbook(w -> {
            var row = w.getSheetAt(0).createRow(2); var original = w.getSheetAt(0).getRow(1);
            for(int i=0;i<7;i++) row.createCell(i).setCellValue(original.getCell(i).getStringCellValue()); row.getCell(0).setCellValue("Second "+admin.id());
        });
        var result = data(preview(upload,"atomic.xlsx",admin));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> { if(calls.incrementAndGet() == 2) throw new IllegalStateException("forced persistence failure"); return call.callRealMethod(); })
                .when(words).create(any(VocabularyWordRequest.class));
        confirm(result.path("id").asText(),admin).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("FAILED"))
                .andExpect(jsonPath("$.result.success").value(false)).andExpect(jsonPath("$.result.importedRows").value(0));
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE word=?",Long.class,"Second "+admin.id())).isZero();
        mvc.perform(get("/api/admin/imports/"+result.path("id").asText()).with(user(admin))).andExpect(jsonPath("$.result.failedRows").value(2));
    }
}
