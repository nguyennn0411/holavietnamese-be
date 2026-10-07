package com.sep490.backend.learning.demo;

import com.sep490.backend.learning.shared.ContentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(110)
@ConditionalOnProperty(name = "app.foundation.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FoundationSeedRunner implements CommandLineRunner {
  private final FoundationCourseSeed seed;
  private final ContentStore db;

  @Override public void run(String... args) throws Exception {
    var admins = db.rows("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1");
    if (admins.isEmpty()) throw new IllegalStateException("Foundation seed needs an existing ADMIN author. Create one before enabling app.foundation.seed.enabled.");
    seed.seed(ContentStore.id(admins.getFirst(), "id"));
  }
}
