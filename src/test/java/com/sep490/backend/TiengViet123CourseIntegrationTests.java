package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.learning.bootstrap.seed.TiengViet123CourseSeed;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TiengViet123CourseIntegrationTests {
  @Autowired ContentStore db;
  @Autowired ObjectMapper json;
  @Autowired TiengViet123CourseSeed seed;
  @Autowired MockMvc mvc;

  @Test
  void textbookImagesArePublicForBrowserLessons() throws Exception {
    var response = mvc.perform(get("/media/tv123/images/lesson-01.jpg"))
        .andExpect(status().isOk()).andReturn().getResponse();
    assertThat(response.getContentType()).startsWith("image/jpeg");
    assertThat(response.getContentAsByteArray().length).isGreaterThan(10_000);
  }

  @Test
  void curriculumPersistsFourLevelsWithTextbookImagesAndRegionalAudio() throws Exception {
    long author = db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,'test',true)",
        UUID.randomUUID() + "@tv123.test");
    Map<String, Long> ids = seed.seed(author);
    assertThat(seed.seed(author)).isEqualTo(ids);
    assertThat(ids.keySet()).containsExactlyElementsOf(TiengViet123CourseSeed.CODES);
    assertThat(db.count("SELECT count(*) FROM courses WHERE code LIKE 'TV123-%'")).isEqualTo(4);
    assertThat(total(ids, "lessons")).isEqualTo(20);
    assertThat(total(ids, "quizzes")).isEqualTo(28);
    assertThat(db.count("SELECT count(*) FROM grammar_topics WHERE code LIKE 'TV123-%'")).isEqualTo(16);
    assertThat(db.rows("SELECT status FROM courses WHERE code LIKE 'TV123-%'"))
        .allSatisfy(row -> assertThat(row.get("status")).isEqualTo("PUBLISHED"));
  }

  @Test
  void sourceFileHasCompleteBilingualMediaBackedLessons() throws Exception {
    JsonNode root;
    try (var in = new ClassPathResource("learning/tv123-courses.json").getInputStream()) {
      root = json.readTree(in);
    }
    int lessons = 0;
    for (JsonNode course : root.path("courses")) {
      for (JsonNode module : course.path("modules")) {
        for (JsonNode lesson : module.path("lessons")) {
          lessons++;
          for (String key : new String[] {"titleVi", "titleEn", "descriptionVi", "descriptionEn", "objectiveVi", "objectiveEn"}) {
            assertThat(lesson.path(key).asText()).isNotBlank();
          }
          assertThat(lesson.path("questions")).hasSizeGreaterThanOrEqualTo(4);
          assertThat(lesson.path("activities").toString()).contains("/media/tv123/images/");
          for (JsonNode activity : lesson.path("activities")) {
            JsonNode content = activity.path("contentJson");
            if (content.hasNonNull("imageUrl")) {
              assertThat(new ClassPathResource("static" + content.path("imageUrl").asText()).exists()).isTrue();
            }
            if (content.hasNonNull("audioUrl")) {
              assertThat(content.path("audioUrl").asText())
                  .startsWith("https://123vietnamese.com/wp-content/uploads/");
            }
          }
        }
      }
    }
    assertThat(lessons).isEqualTo(20);
  }

  private long total(Map<String, Long> ids, String table) {
    return ids.values().stream().mapToLong(id -> db.count(
        "SELECT count(*) FROM " + table + " WHERE course_id=?", id)).sum();
  }
}
