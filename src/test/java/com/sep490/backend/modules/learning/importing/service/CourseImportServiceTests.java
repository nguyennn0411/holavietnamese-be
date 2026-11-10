package com.sep490.backend.modules.learning.importing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.sep490.backend.modules.learning.importing.dto.CourseImportCommand;
import com.sep490.backend.modules.learning.importing.dto.CourseImportError;
import com.sep490.backend.modules.learning.importing.dto.ImportMode;
import com.sep490.backend.modules.learning.importing.dto.ImportSheet;
import com.sep490.backend.modules.learning.importing.exception.CourseImportRejected;
import com.sep490.backend.modules.learning.importing.repository.CourseImportRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseImportServiceTests {
  private ExcelCourseParser parser;
  private CourseImportValidator validator;
  private CourseImportRepository repository;
  private CourseImportService service;
  private CourseImportCommand command;

  @BeforeEach
  void setUp() {
    parser = mock(ExcelCourseParser.class);
    validator = mock(CourseImportValidator.class);
    repository = mock(CourseImportRepository.class);
    service = new CourseImportService(parser, validator, repository);
    var sheets = new EnumMap<ImportSheet, List<CourseImportCommand.Row>>(ImportSheet.class);
    sheets.put(ImportSheet.COURSE, List.of(new CourseImportCommand.Row(
        ImportSheet.COURSE, 2, Map.of("code", "VI-A1"))));
    command = new CourseImportCommand(sheets, List.of(), List.of());
  }

  @Test
  void validateStopsBeforeRepositoryWhenWorkbookRulesFail() {
    when(parser.parse(any(), eq("course.xlsx"))).thenReturn(command);
    var error = new CourseImportError("01_COURSE", 2, "level", "", "Required");
    when(validator.validate(command)).thenReturn(List.of(error));

    var preview = service.validate(new byte[] {1}, "course.xlsx", ImportMode.CREATE_ONLY);

    assertThat(preview.success()).isFalse();
    assertThat(preview.errors()).containsExactly(error);
    verify(repository, never()).validate(any(), any());
  }

  @Test
  void validateIncludesDatabaseCollisionChecksAfterWorkbookIsValid() {
    when(parser.parse(any(), eq("course.xlsx"))).thenReturn(command);
    when(validator.validate(command)).thenReturn(List.of());
    var collision = new CourseImportError("01_COURSE", 2, "code", "VI-A1", "Already exists");
    when(repository.validate(command, ImportMode.CREATE_ONLY)).thenReturn(List.of(collision));

    var preview = service.validate(new byte[] {1}, "course.xlsx", ImportMode.CREATE_ONLY);

    assertThat(preview.success()).isFalse();
    assertThat(preview.courseCode()).isEqualTo("VI-A1");
  }

  @Test
  void successfulImportLocksCourseValidatesAndPersists() {
    when(parser.parse(any(), eq("course.xlsx"))).thenReturn(command);
    when(validator.validate(command)).thenReturn(List.of());
    when(repository.validate(command, ImportMode.UPDATE_EXISTING)).thenReturn(List.of());

    var preview = service.importCourse(new byte[] {1}, "course.xlsx", ImportMode.UPDATE_EXISTING);

    assertThat(preview.success()).isTrue();
    verify(repository).lockCourse("VI-A1");
    verify(repository).save(command);
  }

  @Test
  void rejectedImportNeverPersistsPartialData() {
    when(parser.parse(any(), eq("course.xlsx"))).thenReturn(command);
    when(validator.validate(command)).thenReturn(List.of(
        new CourseImportError("01_COURSE", 2, "code", "VI-A1", "Invalid")));

    assertThatThrownBy(() -> service.importCourse(
        new byte[] {1}, "course.xlsx", ImportMode.CREATE_ONLY))
        .isInstanceOf(CourseImportRejected.class);
    verify(repository, never()).save(any());
  }
}
