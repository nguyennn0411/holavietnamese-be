package com.sep490.backend.modules.learning.seed;

import com.sep490.backend.modules.learning.store.ContentStore;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(130)
@ConditionalOnProperty(name = "app.chapterone.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ChapterOneSeedRunner implements CommandLineRunner {
  private final ChapterOneCourseSeed seed;
  private final ContentStore db;
  @Override public void run(String... args) throws Exception {
    var admins = db.rows("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1");
    if (admins.isEmpty()) throw new IllegalStateException("Pronunciation seed requires an existing ADMIN author.");
    long id = seed.seed(ContentStore.id(admins.getFirst(), "id"));
    org.slf4j.LoggerFactory.getLogger(getClass()).info("Pronunciation course {} persisted as ID {}", ChapterOneCourseSeed.CODE, id);
  }
}
