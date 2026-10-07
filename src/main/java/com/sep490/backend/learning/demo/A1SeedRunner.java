package com.sep490.backend.learning.demo;

import com.sep490.backend.learning.shared.ContentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Uses the existing opt-in demo seed mechanism. Content is persisted once in MySQL. */
@Component
@Profile({"dev-demo", "a1-demo"})
@Order(100)
@RequiredArgsConstructor
public class A1SeedRunner implements CommandLineRunner {
  private final A1CourseSeed seed;
  private final ContentStore db;
  @Override public void run(String... args) throws Exception {
    var admins=db.rows("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1");
    if(admins.isEmpty()) throw new IllegalStateException("A1 seed requires an existing admin account.");
    seed.seed(ContentStore.id(admins.getFirst(),"id"));
  }
}
