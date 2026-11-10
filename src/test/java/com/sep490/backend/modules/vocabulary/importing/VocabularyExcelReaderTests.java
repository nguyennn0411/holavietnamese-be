package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Type;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyExcelReader;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Unit tests for workbook safety limits, schema validation and supported cell conversion. */
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

    @Test
    void rejectsMissingSheetAndWorkbookWithoutDataRows() throws Exception {
        // A syntactically valid XLSX is still invalid when the configured sheet is absent.
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("OTHER");
            var result = reader.read(bytes(workbook), "words.xlsx", type);
            assertThat(result.rows()).isEmpty();
            assertThat(result.errors()).extracting(error -> error.column()).contains("sheet");
        }

        // A header-only workbook must not be accepted as an import with zero work to do.
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet("VOCABULARY").createRow(0);
            header.createCell(0).setCellValue("word");
            header.createCell(1).setCellValue("part_of_speech");
            var result = reader.read(bytes(workbook), "words.xlsx", type);
            assertThat(result.errors())
                    .anySatisfy(error -> assertThat(error.message()).contains("chưa có dòng"));
        }
    }

    @Test
    void reportsUnsupportedDuplicateMissingAndFormulaHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet("VOCABULARY").createRow(0);
            header.createCell(0).setCellValue("word");
            header.createCell(1).setCellValue("word"); // duplicate header
            header.createCell(2).setCellValue("unknown"); // unsupported column
            header.createCell(3).setCellFormula("1+1"); // formulas are unsafe as headers

            var result = reader.read(bytes(workbook), "words.xlsx", type);

            assertThat(result.errors()).extracting(error -> error.message())
                    .anyMatch(message -> message.contains("Header bị trùng"))
                    .anyMatch(message -> message.contains("không được hỗ trợ"))
                    .anyMatch(message -> message.contains("Thiếu cột bắt buộc"))
                    .anyMatch(message -> message.contains("công thức"));
        }
    }

    @Test
    void convertsNumericAndBooleanCellsAndSkipsCompletelyBlankRows() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("VOCABULARY");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("word");
            header.createCell(1).setCellValue("part_of_speech");
            var data = sheet.createRow(1);
            data.createCell(0).setCellValue(12.50);
            data.createCell(1).setCellValue(true);
            sheet.createRow(2); // no values: must not create an import row

            var result = reader.read(bytes(workbook), "words.xlsx", type);

            assertThat(result.rows()).singleElement().satisfies(row -> {
                assertThat(row.values().get("word")).isEqualTo("12.5");
                assertThat(row.values().get("part_of_speech")).isEqualTo("true");
            });
        }
    }

    @Test
    void rejectsCorruptArchivesAndColumnLimit() throws Exception {
        // The extension alone is insufficient: the content must be a real ZIP/XLSX archive.
        assertThat(reader.read(new byte[] {1, 2, 3}, "words.xlsx", type).errors())
                .anySatisfy(error -> assertThat(error.message()).contains("Không đọc được XLSX"));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var header = workbook.createSheet("VOCABULARY").createRow(0);
            for (int index = 0; index < 101; index++) {
                header.createCell(index).setCellValue("column_" + index);
            }
            assertThat(reader.read(bytes(workbook), "words.xlsx", type).errors())
                    .anySatisfy(error -> assertThat(error.message()).contains("Không đọc được XLSX"));
        }
    }

    private static byte[] bytes(XSSFWorkbook workbook) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
