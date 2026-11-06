package com.sep490.backend.modules.learning.importing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.learning.importing.dto.CourseImportCommand;
import com.sep490.backend.modules.learning.importing.dto.ImportSheet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CourseImportValidatorTests {
  private final CourseImportValidator validator = new CourseImportValidator();

  @Test
  void acceptsMinimalValidCourseWorkbook() {
    var command = command(Map.of(ImportSheet.COURSE, List.of(row(ImportSheet.COURSE, 2, Map.of(
        "code", "VI-A1", "level", "A1", "title_en", "Vietnamese A1", "title_vi", "Tiếng Việt A1",
        "description_en", "Description", "description_vi", "Mô tả",
        "estimated_hours", "10", "status", "DRAFT")))));

    assertThat(validator.validate(command)).isEmpty();
  }

  @Test
  void reportsDuplicateCourseAndCaseInsensitiveDuplicateCode() {
    var first = row(ImportSheet.COURSE, 2, courseValues("VI-A1"));
    var second = row(ImportSheet.COURSE, 3, courseValues("vi-a1"));

    var errors = validator.validate(command(Map.of(ImportSheet.COURSE, List.of(first, second))));

    assertThat(errors).extracting("message")
        .anyMatch(message -> message.toString().contains("exactly one course"))
        .anyMatch(message -> message.toString().contains("Duplicate key"));
  }

  @Test
  void reportsOrphanLessonInvalidNumberAndUnsupportedStatus() {
    var lesson = row(ImportSheet.LESSONS, 2, Map.of(
        "unit_code", "MISSING", "lesson_code", "L1", "title_en", "Lesson",
        "title_vi", "Bài học", "description_en", "", "description_vi", "",
        "estimated_minutes", "1.5", "sort_order", "-1", "status", "REVIEW"));
    var errors = validator.validate(command(Map.of(
        ImportSheet.COURSE, List.of(row(ImportSheet.COURSE, 2, courseValues("VI-A1"))),
        ImportSheet.LESSONS, List.of(lesson))));

    assertThat(errors).extracting("column")
        .contains("unit_code", "estimated_minutes", "sort_order", "status");
  }

  @Test
  void choiceExerciseRequiresOptionsAndCorrectOption() {
    var unit = row(ImportSheet.UNITS, 2, Map.of(
        "course_code", "VI-A1", "unit_code", "U1", "title_en", "Unit", "title_vi", "Bài",
        "description_en", "", "description_vi", "", "sort_order", "1"));
    var lesson = row(ImportSheet.LESSONS, 2, Map.of(
        "unit_code", "U1", "lesson_code", "L1", "title_en", "Lesson", "title_vi", "Bài học",
        "description_en", "", "description_vi", "", "estimated_minutes", "10",
        "sort_order", "1", "status", "DRAFT"));
    var exercise = row(ImportSheet.EXERCISES, 2, Map.of(
        "exercise_code", "E1", "lesson_code", "L1", "type", "MULTIPLE_CHOICE",
        "question_vi", "Chọn", "question_en", "Choose", "correct_answer", "a",
        "explanation_vi", "", "explanation_en", "", "difficulty", "EASY", "sort_order", "1"));
    var errors = validator.validate(command(Map.of(
        ImportSheet.COURSE, List.of(row(ImportSheet.COURSE, 2, courseValues("VI-A1"))),
        ImportSheet.UNITS, List.of(unit), ImportSheet.LESSONS, List.of(lesson),
        ImportSheet.EXERCISES, List.of(exercise))));

    assertThat(errors).extracting("message")
        .anyMatch(message -> message.toString().contains("must include options"));
  }

  private static CourseImportCommand command(Map<ImportSheet, List<CourseImportCommand.Row>> rows) {
    var sheets = new EnumMap<ImportSheet, List<CourseImportCommand.Row>>(ImportSheet.class);
    sheets.putAll(rows);
    return new CourseImportCommand(sheets, List.of(), List.of());
  }

  private static CourseImportCommand.Row row(ImportSheet sheet, int number, Map<String, String> values) {
    return new CourseImportCommand.Row(sheet, number, values);
  }

  private static Map<String, String> courseValues(String code) {
    return Map.of(
        "code", code, "level", "A1", "title_en", "Vietnamese A1", "title_vi", "Tiếng Việt A1",
        "description_en", "Description", "description_vi", "Mô tả",
        "estimated_hours", "10", "status", "DRAFT");
  }
}
