package com.sep490.backend.learning.demo;

import com.sep490.backend.learning.shared.ContentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(140)
@ConditionalOnProperty(name = "app.msu-basic.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class BasicVietnameseWorkbookSeedRunner implements CommandLineRunner {
  private final BasicVietnameseWorkbookSeed seed;
  private final ContentStore db;

  @Override
  public void run(String... args) {
    var authors = db.rows("SELECT id FROM users WHERE role IN ('ADMIN', 'CONTENT_MANAGER') ORDER BY id LIMIT 1");
    long authorId;
    if (!authors.isEmpty()) {
      authorId = ContentStore.id(authors.getFirst(), "id");
    } else {
      var anyUser = db.rows("SELECT id FROM users ORDER BY id LIMIT 1");
      if (!anyUser.isEmpty()) {
        authorId = ContentStore.id(anyUser.getFirst(), "id");
      } else {
        log.info("No author user found in database yet; MSU workbook seed will run when an account exists.");
        return;
      }
    }

    int imported = seed.seed(authorId);
    if (imported > 0) {
      log.info("MSU Basic Vietnamese seed completed: {} new courses imported and published.", imported);
    }
  }
}
