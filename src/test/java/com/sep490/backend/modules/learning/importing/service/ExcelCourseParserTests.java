package com.sep490.backend.modules.learning.importing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.learning.importing.dto.ImportSheet;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelCourseParserTests {
  private final ExcelCourseParser parser = new ExcelCourseParser();

  @Test
  void rejectsEmptyWrongExtensionAndMalformedArchive() {
    assertThat(parser.parse(new byte[0], "course.xlsx").errors()).isNotEmpty();
    assertThat(parser.parse(new byte[] {1}, "course.xls").errors()).isNotEmpty();
    assertThat(parser.parse(new byte[] {1, 2, 3}, "course.xlsx").errors())
        .extracting("column").contains("file");
  }

  @Test
  void reportsEveryMissingRequiredSheet() throws Exception {
    byte[] bytes;
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      workbook.createSheet("OTHER");
      workbook.write(output);
      bytes = output.toByteArray();
    }

    var command = parser.parse(bytes, "course.xlsx");

    assertThat(command.errors()).hasSize(ImportSheet.values().length);
    assertThat(command.warnings()).extracting("sheet").contains("OTHER");
  }

  @Test
  void parsesTrimmedTextAndNumericValuesFromCompleteWorkbook() throws Exception {
    byte[] bytes;
    try (var workbook = completeWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.getSheet(ImportSheet.COURSE.sheetName);
      var row = sheet.createRow(1);
      set(row, ImportSheet.COURSE, "code", "  VI-A1  ");
      set(row, ImportSheet.COURSE, "level", "A1");
      set(row, ImportSheet.COURSE, "title_en", "Vietnamese A1");
      set(row, ImportSheet.COURSE, "title_vi", "Tiếng Việt A1");
      setNumeric(row, ImportSheet.COURSE, "estimated_hours", 12.5);
      set(row, ImportSheet.COURSE, "status", "DRAFT");
      workbook.write(output);
      bytes = output.toByteArray();
    }

    var command = parser.parse(bytes, "COURSE.XLSX");

    assertThat(command.errors()).isEmpty();
    assertThat(command.rows(ImportSheet.COURSE)).hasSize(1);
    assertThat(command.courseCode()).isEqualTo("VI-A1");
    assertThat(command.rows(ImportSheet.COURSE).getFirst().get("estimated_hours"))
        .isEqualTo("12.5");
  }

  @Test
  void formulaCellsAreRejectedInsteadOfEvaluated() throws Exception {
    byte[] bytes;
    try (var workbook = completeWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.getSheet(ImportSheet.COURSE.sheetName);
      var row = sheet.createRow(1);
      set(row, ImportSheet.COURSE, "code", "VI-A1");
      set(row, ImportSheet.COURSE, "level", "A1");
      set(row, ImportSheet.COURSE, "title_en", "Vietnamese A1");
      set(row, ImportSheet.COURSE, "title_vi", "Tiếng Việt A1");
      cell(row, ImportSheet.COURSE, "estimated_hours").setCellFormula("5+5");
      set(row, ImportSheet.COURSE, "status", "DRAFT");
      workbook.write(output);
      bytes = output.toByteArray();
    }

    assertThat(parser.parse(bytes, "course.xlsx").errors()).extracting("message")
        .anyMatch(message -> message.toString().contains("Formula"));
  }

  private static XSSFWorkbook completeWorkbook() {
    var workbook = new XSSFWorkbook();
    for (ImportSheet spec : ImportSheet.values()) {
      var row = workbook.createSheet(spec.sheetName).createRow(0);
      for (int index = 0; index < spec.columns.size(); index++)
        row.createCell(index).setCellValue(spec.columns.get(index));
    }
    return workbook;
  }

  private static void set(org.apache.poi.ss.usermodel.Row row, ImportSheet sheet, String column, String value) {
    cell(row, sheet, column).setCellValue(value);
  }

  private static void setNumeric(org.apache.poi.ss.usermodel.Row row, ImportSheet sheet, String column, double value) {
    cell(row, sheet, column).setCellValue(value);
  }

  private static org.apache.poi.ss.usermodel.Cell cell(
      org.apache.poi.ss.usermodel.Row row, ImportSheet sheet, String column) {
    return row.createCell(sheet.columns.indexOf(column));
  }
}
