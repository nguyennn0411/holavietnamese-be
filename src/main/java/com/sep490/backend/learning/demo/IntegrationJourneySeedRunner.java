package com.sep490.backend.learning.demo;

import com.sep490.backend.learning.shared.ContentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(180)
@ConditionalOnProperty(name = "app.integration-demo.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class IntegrationJourneySeedRunner implements CommandLineRunner {
  private final IntegrationJourneySeed seed;
  private final ContentStore db;

  @Override
  public void run(String... args) throws Exception {
    long author = ContentStore.id(db.one("SELECT id FROM users WHERE role='CONTENT_MANAGER' ORDER BY id LIMIT 1"), "id");
    long reviewer = ContentStore.id(db.one("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1"), "id");
    log.info("Integration demo course ready: courseId={}, code={}", seed.seed(author, reviewer), IntegrationJourneySeed.CODE);
  }
}
