package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.support.ContentApiTestSupport;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

class VocabularyImportApiTests extends ContentApiTestSupport {
    @Autowired ObjectMapper json;

    private LearnerPrincipal contentUser() {
        String email = UUID.randomUUID() + "@import.test";
        jdbc.update(
                "INSERT INTO users(username,email,password_hash) VALUES(?,?,'test')", email, email);
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);
        return new LearnerPrincipal(id, email, "", true, "CONTENT");
    }

    private byte[] workbook(String word) throws Exception {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("VOCABULARY");
            var columns =
                    List.of("word", "part_of_speech", "translation_en", "example_vi", "example_en");
            var values = List.of(word, "noun", "Imported meaning", "Vi du", "Example");
            var header = sheet.createRow(0);
            var row = sheet.createRow(1);
            for (int index = 0; index < columns.size(); index++) {
                header.createCell(index).setCellValue(columns.get(index));
                row.createCell(index).setCellValue(values.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private JsonNode preview(String word, LearnerPrincipal actor) throws Exception {
        var result =
                mvc.perform(
                                multipart("/api/admin/imports/preview")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        "words.xlsx",
                                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                        workbook(word)))
                                        .param("module", "VOCABULARY")
                                        .with(user(actor))
                                        .with(csrf()))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("result");
    }

    @Test
    void previewIsReadOnlyAndConfirmIsOwnedPersistentAndIdempotent() throws Exception {
        var owner = contentUser();
        var other = contentUser();
        String word = "api-import-" + owner.id();
        var preview = preview(word, owner);
        assertThat(preview.path("canConfirm").asBoolean()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabularies WHERE word=?",
                                Integer.class,
                                word))
                .isZero();
        String path = "/api/admin/imports/" + preview.path("id").asText();
        mvc.perform(post(path + "/confirm").with(user(other)).with(csrf()))
                .andExpect(status().isForbidden());
        for (int retry = 0; retry < 2; retry++) {
            mvc.perform(post(path + "/confirm").with(user(owner)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.importedRows").value(1));
        }
        mvc.perform(get(path).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.actorId").value(owner.id()))
                .andExpect(jsonPath("$.result.completedAt").isNotEmpty());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabularies WHERE word=?",
                                Integer.class,
                                word))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabulary_examples e JOIN"
                                    + " vocabulary_meanings m ON m.id=e.vocabulary_meaning_id JOIN"
                                    + " vocabularies v ON v.id=m.vocabulary_id WHERE v.word=?",
                                Integer.class,
                                word))
                .isEqualTo(1);
    }

    @Test
    void editedPreviewAndNewDatabaseDuplicatesAreRecheckedAtConfirmation() throws Exception {
        var owner = contentUser();
        String word = "preview-edit-" + owner.id();
        var preview = preview(word, owner);
        String path = "/api/admin/imports/" + preview.path("id").asText();
        var rows = preview.path("rows").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) rows.get(0).path("values"))
                .put("word", word + "-edited");
        var body = json.createObjectNode().set("rows", rows);
        mvc.perform(
                        put(path + "/preview")
                                .with(user(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk());
        jdbc.update(
                "INSERT INTO vocabularies(word,part_of_speech,status) VALUES(?,'noun','DRAFT')",
                word + "-edited");
        mvc.perform(post(path + "/confirm").with(user(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.importedRows").value(0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabularies WHERE word=?",
                                Integer.class,
                                word))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabularies WHERE word=?",
                                Integer.class,
                                word + "-edited"))
                .isEqualTo(1);
    }

    @Test
    void templateAndImportRoutesKeepTheirPermissionAndColumnContracts() throws Exception {
        mvc.perform(get("/api/admin/imports/types").with(user(LEARNER)))
                .andExpect(status().isForbidden());
        var owner = contentUser();
        mvc.perform(get("/api/admin/imports/types").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value("VOCABULARY"));
        byte[] template =
                mvc.perform(
                                get("/api/admin/imports/template")
                                        .param("module", "VOCABULARY")
                                        .with(user(owner)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(template))) {
            var header = workbook.getSheet("VOCABULARY").getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("word");
            assertThat(header.getCell(header.getLastCellNum() - 1).getStringCellValue())
                    .isEqualTo("regional_usage_note_en");
        }
    }
}
