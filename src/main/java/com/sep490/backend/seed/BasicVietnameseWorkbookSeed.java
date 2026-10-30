package com.sep490.backend.seed;

import com.sep490.backend.modules.learning.authoring.application.CourseContentService;
import com.sep490.backend.modules.learning.importing.service.CourseWorkbookService;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatically imports and publishes the 15 MSU Basic Vietnamese authored workbooks
 * from classpath:learning/workbooks/ into the course curriculum.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicVietnameseWorkbookSeed {
  private final CourseWorkbookService workbooks;
  private final CourseContentService courses;
  private final ContentStore db;

  private static final List<String> WORKBOOK_FILES = List.of(
      "HolaVietnamese_Chuong1_DaSua_LessonType.xlsx",
      "HolaVietnamese_Chapter2_QuocGia_NgonNgu.xlsx",
      "HolaVietnamese_Chapter3_SoDem_VaCachDem.xlsx",
      "HolaVietnamese_Chapter04_Telling_the_Time.xlsx",
      "HolaVietnamese_Chapter05_Days_and_Dates.xlsx",
      "HolaVietnamese_Chapter06_Family.xlsx",
      "HolaVietnamese_Chapter07_Accommodation.xlsx",
      "HolaVietnamese_Chapter08_Weather.xlsx",
      "HolaVietnamese_Chapter09_School_and_Study.xlsx",
      "HolaVietnamese_Chapter10_Occupations.xlsx",
      "HolaVietnamese_Chapter11_Colors_and_Descriptions.xlsx",
      "HolaVietnamese_Chapter12_Food_and_Drinks.xlsx",
      "HolaVietnamese_Chapter13_Shopping.xlsx",
      "HolaVietnamese_Chapter14_Traffic_and_Transport.xlsx",
      "HolaVietnamese_Chapter15_News_and_the_Media.xlsx"
  );

  public int seed(long author) {
    int importedCount = 0;
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    for (String fileName : WORKBOOK_FILES) {
      try {
        Resource resource = resolver.getResource("classpath:learning/workbooks/" + fileName);
        if (!resource.exists()) {
          log.warn("Workbook resource not found: learning/workbooks/{}", fileName);
          continue;
        }

        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
          bytes = in.readAllBytes();
        }

        String courseCode = extractCourseCode(bytes);
        if (courseCode != null && db.count("SELECT count(*) FROM courses WHERE code=?", courseCode) > 0) {
          log.debug("Course {} already exists in database, skipping.", courseCode);
          continue;
        }

        Map<String, Object> preview = workbooks.validate(bytes, fileName);
        if (!Boolean.TRUE.equals(preview.get("valid"))) {
          log.error("Workbook {} validation failed: {}", fileName, preview.get("errors"));
          continue;
        }

        Map<String, Object> result = workbooks.importDraft(bytes, fileName, author);
        long courseId = ((Number) result.get("courseId")).longValue();

        // Publish the imported course so learners can immediately access it
        courses.status(courseId, "PUBLISHED");
        importedCount++;
        log.info("Successfully imported and published course {} (ID: {}) from {}", courseCode, courseId, fileName);

      } catch (Exception ex) {
        log.error("Failed to import workbook {}: {}", fileName, ex.getMessage(), ex);
      }
    }

    log.info("MSU Basic Vietnamese workbook seeding summary: newly imported {}/{} courses.", importedCount, WORKBOOK_FILES.size());
    return importedCount;
  }

  private String extractCourseCode(byte[] bytes) {
    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = wb.getSheet("COURSE");
      if (sheet == null) return null;
      Row header = sheet.getRow(0);
      Row data = sheet.getRow(1);
      if (header == null || data == null) return null;
      int codeCol = -1;
      for (Cell c : header) {
        if ("code".equalsIgnoreCase(c.getStringCellValue().strip())) {
          codeCol = c.getColumnIndex();
          break;
        }
      }
      if (codeCol < 0) return null;
      Cell valCell = data.getCell(codeCol);
      return valCell == null ? null : valCell.getStringCellValue().strip();
    } catch (Exception ex) {
      return null;
    }
  }
}
