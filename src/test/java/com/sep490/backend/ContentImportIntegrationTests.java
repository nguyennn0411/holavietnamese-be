package com.sep490.backend;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.service.VocabularyWordService;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyWordRequest;
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
        admin = principal("CONTENT_MANAGER",Set.of("VOCABULARY_PUBLISH"));
        other = principal("CONTENT_MANAGER",Set.of("VOCABULARY_PUBLISH"));
        learner = principal("LEARNER",Set.of());
    }
    private LearnerPrincipal principal(String role, Set<String> permissions) {
        String name = "import-"+UUID.randomUUID().toString().substring(0,12)+"@example.test";
        jdbc.update("INSERT INTO users(username,email,password_hash) VALUES(?,?,'unused')",name,name);
        long id = jdbc.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
        return new LearnerPrincipal(id,name,"",true,role,Set.of(role),permissions);
    }
    private byte[] workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("VOCABULARY");
            var header = sheet.createRow(0); var row = sheet.createRow(1);
            var columns = List.of("word","part_of_speech","meaning_order","translation_en","status","example_vi","example_en");
            var values = List.of("Từ import "+admin.id(),"noun","0","Imported word","DRAFT","Một câu ví dụ.","An example sentence.");
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
            w.getSheetAt(0).getRow(0).createCell(7).setCellValue("unsupported_column"); row.createCell(7).setCellValue("999999");
        }),"invalid.xlsx",admin).andExpect(jsonPath("$.result.canConfirm").value(false)).andExpect(jsonPath("$.result.errorRows").value(1)));
        confirm(invalid.path("id").asText(),admin).andExpect(status().isConflict());
        preview(workbook(w -> { var row = w.getSheetAt(0).createRow(2); var original = w.getSheetAt(0).getRow(1); for(int i=0;i<7;i++) row.createCell(i).setCellValue(original.getCell(i).getStringCellValue()); row.getCell(3).setCellValue("Conflicting meaning"); }),"duplicate.xlsx",admin)
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
    @Test void routesRequireContentPermissionAndTemplateHasTheActualHeaderContract() throws Exception {
        preview(workbook(w -> {}),"valid.xlsx",learner).andExpect(status().isForbidden());
        preview(workbook(w -> {}),"valid.xlsx",new LearnerPrincipal(899903L,"system-admin@example.test","",true,"ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/imports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/imports/types").with(user(admin))).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].id").value("VOCABULARY"));
        var file = mvc.perform(get("/api/admin/imports/template").param("module","VOCABULARY").with(user(admin))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file))) { assertThat(book.getSheet("VOCABULARY").getRow(0).getCell(0).getStringCellValue()).isEqualTo("word"); }
        mvc.perform(get("/api/admin/imports/template").param("module","USER").with(user(admin))).andExpect(status().isBadRequest());
    }
    @Test void excelPreservesMultipleBilingualMeaningsExamplesAndOptionalFields() throws Exception {
        byte[] upload;
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("VOCABULARY");
            var headers=List.of("word","part_of_speech","pronunciation","audio_url","status","meaning_order",
                    "definition_vi","translation_en","definition_en","usage_note","labels","example_order","example_vi","example_en","example_audio_url");
            var header=sheet.createRow(0);
            for(int i=0;i<headers.size();i++) header.createCell(i).setCellValue(headers.get(i));
            var values=List.of(
                    List.of("Từ import "+admin.id(),"noun","/ka fe/","/coffee.mp3","DRAFT","0","Đồ uống từ hạt cà phê.","coffee","A drink.","Dùng khi gọi món.","thông dụng\nẩm thực","0","Tôi uống cà phê.","I drink coffee.","/example.mp3"),
                    List.of("Từ import "+admin.id(),"noun","","","","0","","","","","","1","Một tách cà phê.","A cup of coffee.",""),
                    List.of("Từ import "+admin.id(),"noun","","","","1","Một nghĩa chỉ có tiếng Việt.","","","Ghi chú nghĩa thứ hai.","","0","Câu ví dụ tiếng Việt.","",""));
            for(int r=0;r<values.size();r++) {
                var row=sheet.createRow(r+1);
                for(int c=0;c<headers.size();c++) row.createCell(c).setCellValue(values.get(r).get(c));
            }
            book.write(out);upload=out.toByteArray();
        }
        var job=data(preview(upload,"complete.xlsx",admin).andExpect(jsonPath("$.result.canConfirm").value(true)));
        assertThat(count()).isZero();
        confirm(job.path("id").asText(),admin).andExpect(jsonPath("$.result.importedRows").value(1));
        mvc.perform(get("/api/admin/vocabulary/search").with(user(admin)).param("keyword","coffee"))
                .andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].audioUrl").value("/coffee.mp3"))
                .andExpect(jsonPath("$.result.content[0].meanings.length()").value(2))
                .andExpect(jsonPath("$.result.content[0].meanings[0].definitionVi").value("Đồ uống từ hạt cà phê."))
                .andExpect(jsonPath("$.result.content[0].meanings[0].definitionEn").value("A drink."))
                .andExpect(jsonPath("$.result.content[0].meanings[0].usageNote").value("Dùng khi gọi món."))
                .andExpect(jsonPath("$.result.content[0].meanings[0].labels.length()").value(2))
                .andExpect(jsonPath("$.result.content[0].meanings[0].examples.length()").value(2))
                .andExpect(jsonPath("$.result.content[0].meanings[0].examples[0].audioUrl").value("/example.mp3"))
                .andExpect(jsonPath("$.result.content[0].meanings[0].examples[1].translationEn").value("A cup of coffee."))
                .andExpect(jsonPath("$.result.content[0].meanings[1].translationEn").isEmpty())
                .andExpect(jsonPath("$.result.content[0].cefrLevel").doesNotExist())
                .andExpect(jsonPath("$.result.content[0].topics").doesNotExist());
        mvc.perform(get("/api/admin/imports/template").param("module","REMOVED_IMPORT").with(user(admin)))
                .andExpect(status().isBadRequest());
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
