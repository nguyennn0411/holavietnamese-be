package com.sep490.backend.modules.learning.seed;

import com.sep490.backend.modules.learning.store.ContentStore;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(140)
@ConditionalOnProperty(name="app.chaptertwo.seed.enabled",havingValue="true")
@RequiredArgsConstructor
public class ChapterTwoSeedRunner implements CommandLineRunner {
  private final ChapterTwoCourseSeed seed;
  private final ContentStore db;
  @Override public void run(String... args) throws Exception {
    var admins=db.rows("SELECT id FROM users WHERE role='ADMIN' ORDER BY id LIMIT 1");
    if(admins.isEmpty())throw new IllegalStateException("Chapter 2 seed requires an existing ADMIN author.");
    long id=seed.seed(ContentStore.id(admins.getFirst(),"id"));
    org.slf4j.LoggerFactory.getLogger(getClass()).info("Chapter 2 course {} persisted as ID {}",ChapterTwoCourseSeed.CODE,id);
  }
}
