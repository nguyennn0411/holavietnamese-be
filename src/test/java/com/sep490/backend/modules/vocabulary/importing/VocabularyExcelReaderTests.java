package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Type;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyExcelReader;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

class VocabularyExcelReaderTests {
    private final VocabularyExcelReader reader = new VocabularyExcelReader();
    private final Type type =
            new Type(
                    "VOCABULARY",
                    "Tu vung",
                    "VOCABULARY",
                    List.of("word", "part_of_speech"),
                    List.of("word", "part_of_speech"));

    @Test
    void rejectsEmptyAndUnsupportedFiles() {
        assertThat(reader.read(new byte[0], "empty.xlsx", type).errors()).isNotEmpty();
        assertThat(reader.read(new byte[] {1}, "words.csv", type).errors()).isNotEmpty();
    }

    @Test
    void readsUnicodeValuesAndRejectsFormulaCells() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("VOCABULARY");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("word");
            header.createCell(1).setCellValue("part_of_speech");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Cảm ơn");
            row.createCell(1).setCellValue("phrase");
            workbook.write(output);
            var valid = reader.read(output.toByteArray(), "words.xlsx", type);
            assertThat(valid.errors()).isEmpty();
            assertThat(valid.rows().getFirst().values().get("word")).isEqualTo("Cảm ơn");
            output.reset();
            row.getCell(0).setCellFormula("1+1");
            workbook.write(output);
            assertThat(reader.read(output.toByteArray(), "words.xlsx", type).errors()).isNotEmpty();
        }
    }
}
