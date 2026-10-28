package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.course.CourseWorkbookService;
import com.sep490.backend.learning.shared.ContentStore;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ImportTainguyenWorkbooksTest {
  @Autowired CourseWorkbookService workbooks;
  @Autowired CourseContentService courses;
  @Autowired ContentStore db;

  @Test
  void importsAndPublishesAll15Chapters() throws Exception {
    long author = db.insert(
        "INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','CONTENT_MANAGER')",
        "cm-" + UUID.randomUUID() + "@test", "cm-" + UUID.randomUUID(), "test"
    );

    List<String> fileOrder = List.of(
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

    int count = 0;
    List<Long> courseIds = new ArrayList<>();
    for (String fileName : fileOrder) {
      File f = new File("src/main/resources/learning/workbooks/" + fileName);
      if (!f.exists()) {
        f = new File("C:/tainguyen/" + fileName);
      }
      assertThat(f.exists()).isTrue();

      byte[] bytes = Files.readAllBytes(f.toPath());
      Map<String, Object> preview = workbooks.validate(bytes, fileName);
      assertThat(preview.get("valid")).describedAs("Validation of " + fileName).isEqualTo(true);

      Map<String, Object> imported = workbooks.importDraft(bytes, fileName, author);
      long courseId = ((Number) imported.get("courseId")).longValue();
      courseIds.add(courseId);

      // Publish the course so learners can see it
      courses.status(courseId, "PUBLISHED");

      var detail = courses.detail(courseId, null, false);
      assertThat(detail).isNotNull();
      assertThat(detail.get("status")).isEqualTo("PUBLISHED");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> modules = (List<Map<String, Object>>) detail.get("modules");
      assertThat(modules).isNotEmpty();

      int lessonCount = ((Number) detail.get("totalLessons")).intValue();
      System.out.println("Imported & Published " + fileName + " => Course ID: " + courseId + ", title: " + detail.get("title") + ", lessons: " + lessonCount);
      count++;
    }

    assertThat(count).isEqualTo(15);
    System.out.println("SUCCESSFULLY IMPORTED AND PUBLISHED ALL 15 COURSES!");
  }
}
