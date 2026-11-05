package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class C1CourseCatalogIntegrationTests {
  @Autowired ContentStore db;
  @Autowired MockMvc mvc;

  @Test
  void c1CourseIsPublishedWithAUsableLessonSequence() throws Exception {
    var course = db.one("SELECT id, level, status FROM courses WHERE code=?", "HOLA-C1-ACADEMIC");
    long courseId = ContentStore.id(course, "id");

    assertThat(course).containsEntry("level", "C1").containsEntry("status", "PUBLISHED");
    assertThat(db.count("SELECT count(*) FROM course_units WHERE course_id=? AND status='PUBLISHED'", courseId))
        .isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", courseId))
        .isEqualTo(3);

    mvc.perform(get("/api/courses/" + courseId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value("C1"))
        .andExpect(jsonPath("$.totalLessons").value(3))
        .andExpect(jsonPath("$.modules[0].lessons.length()").value(3));
  }
}
