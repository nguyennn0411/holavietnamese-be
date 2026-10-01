package com.sep490.backend;


import com.sep490.backend.dto.courseimport.ImportMode;
import com.sep490.backend.service.CourseImportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in smoke test for the authored workbook; all imported data is rolled back. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "course.workbook", matches = ".+")
class CourseWorkbookFileTests {
    @Autowired CourseImportService validator;
    @Autowired CourseImportService importer;
    @Autowired JdbcTemplate jdbc;

    @Test void validatesAndImportsVietnameseForEveryoneWorkbook() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:hola-tests");
        }
        var path = Path.of(System.getProperty("course.workbook"));
        var bytes = Files.readAllBytes(path);
        var preview = validator.validate(bytes, path.getFileName().toString(), ImportMode.CREATE_ONLY);
        assertThat(preview.errors()).isEmpty();
        assertThat(preview.success()).isTrue();
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.statistics()).containsAllEntriesOf(Map.of(
            "courses", 1, "units", 25, "lessons", 25, "blocks", 225,
            "vocabulary", 150, "dialogues", 25, "dialogueLines", 150,
            "drills", 75, "exercises", 125, "options", 75));
        assertThat(importer.importCourse(bytes, path.getFileName().toString(), ImportMode.CREATE_ONLY).success()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from lessons where code like 'VFE-%'", Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select title_vi from lessons where code = 'VFE-U00-L01'", String.class))
            .isEqualTo("Chữ viết, âm và thanh điệu");
        assertThat(jdbc.queryForObject("select status from courses where code = 'VFE-EN-FOUNDATIONS'", String.class)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select count(*) from lessons where code like 'VFE-%' and publication_status = 'DRAFT'", Integer.class)).isEqualTo(25);
    }
}
