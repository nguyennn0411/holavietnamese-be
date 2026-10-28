package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.demo.BasicVietnameseWorkbookSeed;
import com.sep490.backend.learning.shared.ContentStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BasicVietnameseWorkbookSeedTest {
  @Autowired BasicVietnameseWorkbookSeed seed;
  @Autowired CourseContentService courses;
  @Autowired ContentStore db;

  @Test
  void seedsAll15WorkbooksAndIsIdempotent() {
    long admin = db.insert(
        "INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','ADMIN')",
        "admin-" + UUID.randomUUID() + "@test", "admin-" + UUID.randomUUID(), "test"
    );

    // Initial run should import all 15 courses
    int firstImportCount = seed.seed(admin);
    assertThat(firstImportCount).isEqualTo(15);

    // Verify all 15 courses are in published status
    long publishedCount = db.count("SELECT count(*) FROM courses WHERE code LIKE 'MSU-BASIC-VI-%' AND status='PUBLISHED'");
    assertThat(publishedCount).isEqualTo(15L);

    // Second run must be completely idempotent (0 newly imported)
    int secondImportCount = seed.seed(admin);
    assertThat(secondImportCount).isEqualTo(0);

    // Verify learner list endpoint includes all 15 courses
    var listResponse = courses.list(Map.of("size", "100"), false);
    assertThat(listResponse.content()).isNotEmpty();
    long msuCount = listResponse.content().stream()
        .filter(c -> String.valueOf(c.get("code")).startsWith("MSU-BASIC-VI-"))
        .count();
    assertThat(msuCount).isEqualTo(15);
  }
}
