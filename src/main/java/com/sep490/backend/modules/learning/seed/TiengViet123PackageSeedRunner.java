package com.sep490.backend.modules.learning.seed;

import com.sep490.backend.modules.learning.store.ContentStore;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(161)
@ConditionalOnProperty(name = "app.tv123.package-seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TiengViet123PackageSeedRunner implements CommandLineRunner {
  private final TiengViet123PackageSeed seed;
  private final ContentStore db;

  @Override
  public void run(String... args) throws Exception {
    var admins = db.rows("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1");
    if (admins.isEmpty())
      throw new IllegalStateException("TV123 package seed requires an ADMIN author.");
    var ids = seed.seed(ContentStore.id(admins.getFirst(), "id"));
    org.slf4j.LoggerFactory.getLogger(getClass()).info("TV123 package courses persisted: {}", ids);
  }
}
