package com.sep490.backend.service.websiteimport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.CourseContentService;
import com.sep490.backend.service.ActivityService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class WebsiteImportServiceTest {
  private JdbcTemplate jdbc;
  private CourseContentService courses;
  private ActivityService activities;
  private WebsiteImportService service;
  private final LearnerPrincipal admin = new LearnerPrincipal(7L, "admin@example.com", "x", true, "ADMIN");

  @BeforeEach
  void setUp() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:website-import-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("""
        CREATE TABLE website_import_jobs(
          id VARCHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL, mode VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL,
          source_url VARCHAR(2048), source_filename VARCHAR(255), source_fingerprint VARCHAR(64) NOT NULL,
          target_course_id BIGINT, result_course_id BIGINT, title VARCHAR(500) NOT NULL, author VARCHAR(500),
          license VARCHAR(500), attribution CLOB, rights_confirmed BOOLEAN DEFAULT FALSE, payload CLOB NOT NULL,
          diagnostics CLOB NOT NULL, created_at TIMESTAMP, updated_at TIMESTAMP, completed_at TIMESTAMP)
        """);
    jdbc.execute("""
        CREATE TABLE website_import_provenance(
          id BIGINT AUTO_INCREMENT PRIMARY KEY, import_job_id VARCHAR(36), lesson_id BIGINT, activity_id BIGINT,
          block_key VARCHAR(120), source_page_url VARCHAR(2048), original_url VARCHAR(2048), attribution CLOB,
          license VARCHAR(500), import_status VARCHAR(40), source_fingerprint VARCHAR(64))
        """);
    jdbc.execute("CREATE TABLE course_units(id BIGINT PRIMARY KEY, status VARCHAR(20))");
    jdbc.update("INSERT INTO course_units(id,status) VALUES(11,'PUBLISHED')");
    SafeWebFetcher safeWeb = new SafeWebFetcher();
    WebsiteCourseExtractor extractor = new WebsiteCourseExtractor(safeWeb);
    courses = mock(CourseContentService.class); activities = mock(ActivityService.class);
    RemoteMediaLibraryService media = mock(RemoteMediaLibraryService.class);
    when(courses.create(any(), eq(7L))).thenReturn(91L);
    when(courses.addModule(eq(91L), any())).thenReturn(11L);
    when(courses.addLesson(eq(11L), any())).thenReturn(22L);
    AtomicLong activityId = new AtomicLong(30);
    when(activities.create(eq(22L), any())).thenAnswer(invocation -> activityId.incrementAndGet());
    service = new WebsiteImportService(extractor, jdbc, new ObjectMapper(), courses, activities, media, safeWeb,
        Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void fileAnalysisSavesDraftCourseBlocksAndIsIdempotent() {
    String xhtml = """
        <html><head><title>Basic Vietnamese</title><meta name="author" content="Tung Hoang"></head>
        <body><main><h1>Vietnamese Alphabet</h1><p>29 letters: A, Ă, Â, Đ, Ê, Ô, Ơ, Ư.</p><table><tr><td>Đ</td><td>/ɗ/</td></tr></table></main></body></html>
        """;
    var analyzed = service.analyzeFile(xhtml.getBytes(StandardCharsets.UTF_8), "book.xhtml", null, admin);
    assertThat(analyzed.status()).isEqualTo("ANALYZED");
    assertThat(analyzed.stats().lessons()).isEqualTo(1);
    assertThat(analyzed.stats().tables()).isEqualTo(1);

    var saved = service.save(analyzed.id(), new WebsiteImportModel.SaveRequest(true, "CC BY-NC 4.0", "Basic Vietnamese — Tung Hoang"), admin);
    assertThat(saved.status()).isEqualTo("SAVED");
    assertThat(saved.resultCourseId()).isEqualTo(91L);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM website_import_provenance", Long.class)).isEqualTo(saved.stats().blocks());
    verify(courses).create(any(), eq(7L));
    verify(courses).addModule(eq(91L), any());
    verify(courses).addLesson(eq(11L), any());
    verify(activities, times(saved.stats().blocks())).create(eq(22L), any());

    var repeated = service.save(analyzed.id(), new WebsiteImportModel.SaveRequest(true, null, null), admin);
    assertThat(repeated.resultCourseId()).isEqualTo(91L);
    verifyNoMoreInteractions(courses);
  }
}
