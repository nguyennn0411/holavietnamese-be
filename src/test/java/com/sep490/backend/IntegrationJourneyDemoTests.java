package com.sep490.backend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.sep490.backend.dto.request.*;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.learning.demo.IntegrationJourneySeed;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.QuizService;
import com.sep490.backend.learning.shared.ContentStore;
import com.sep490.backend.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IntegrationJourneyDemoTests {
  @Autowired ContentStore db;
  @Autowired IntegrationJourneySeed seed;
  @Autowired JourneyService journey;
  @Autowired DestinationService destinations;
  @Autowired ActivityService activities;
  @Autowired QuizService quizzes;
  @Autowired VocabularyService notebook;
  @Autowired jakarta.persistence.EntityManager entities;

  private long user(String role) {
    return db.insert("INSERT INTO users(email,password_hash,enabled,role) VALUES(?,'test',true,?)",
        UUID.randomUUID() + "@integration.test", role);
  }

  @Test void demoUsesSharedVocabularyAndUnlocksTheNextDestinationOnlyAfterRealLearning() throws Exception {
    long manager = user("CONTENT_MANAGER"), admin = user("ADMIN"), learner = user("LEARNER");
    long course = seed.seed(manager, admin);
    assertThat(seed.seed(manager, admin)).isEqualTo(course);
    // The runner and learner HTTP calls use separate persistence contexts in the running app.
    entities.flush();
    entities.clear();
    assertThat(db.count("SELECT count(*) FROM vocabularies")).isEqualTo(18);
    assertThat(db.count("SELECT count(*) FROM destination_lessons")).isEqualTo(6);
    assertThat(db.count("SELECT count(*) FROM destination_vocabularies")).isEqualTo(18);
    assertThat(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", course)).isEqualTo(6);
    assertThat(db.one("SELECT status,submitted_by,reviewed_by FROM courses WHERE id=?", course))
        .containsEntry("status", "PUBLISHED").containsEntry("submittedBy", manager).containsEntry("reviewedBy", admin);
    db.insert("INSERT INTO enrollments(user_id,course_id,status,enrolled_at) VALUES(?,?,'ACTIVE',CURRENT_TIMESTAMP)", learner, course);
    var stops = journey.configuration().stops();
    assertThat(stops).hasSize(3);
    assertThat(journey.journey(learner, null, null).stops().get(1).accessible()).isFalse();
    assertThatThrownBy(() -> journey.complete(learner, stops.getFirst().destination().id())).isInstanceOf(RuntimeException.class);
    for (var stop : stops) {
      long destination = stop.destination().id();
      journey.start(learner, destination);
      var lessonRows = db.rows("SELECT lesson_id FROM destination_lessons WHERE destination_id=? ORDER BY display_order", destination);
      for (var row : lessonRows) {
        long lesson = ContentStore.id(row, "lessonId");
        var vocabularyActivity = activities.activities(lesson, learner, false).stream()
            .filter(a -> "VOCABULARY".equals(a.get("activityType"))).findFirst().orElseThrow();
        var item = ((JsonNode) vocabularyActivity.get("contentJson")).path("items").get(0);
        long word = item.path("masterVocabularyId").asLong();
        assertThat(word).isPositive();
        assertThat(db.count("SELECT count(*) FROM destination_vocabularies WHERE destination_id=? AND vocabulary_id=?", destination, word)).isEqualTo(1);
        var saved = notebook.add(learner, new VocabularyRequest(lesson, item.path("wordVi").asText(),
            item.path("meaningEn").asText(), null, item.path("exampleVi").asText(), null, word));
        assertThat(db.one("SELECT lesson_id,master_vocabulary_id FROM vocabulary_entries WHERE id=?", saved.id()))
            .containsEntry("lessonId", lesson).containsEntry("masterVocabularyId", word);
        for (var activity : db.rows("SELECT id,activity_type,quiz_id FROM lesson_activities WHERE lesson_id=? ORDER BY order_index", lesson)) {
          if ("QUIZ".equals(activity.get("activityType"))) {
            long attempt = ContentStore.id(quizzes.start(learner, ContentStore.id(activity, "quizId")), "id");
            for (var question : db.rows("SELECT a.question_id,v.correct_answer_json FROM quiz_answers a JOIN question_versions v ON v.id=a.question_version_id WHERE a.attempt_id=?", attempt))
              quizzes.save(learner, attempt, ContentStore.id(question, "questionId"), db.json(question.get("correctAnswerJson")));
            quizzes.submit(learner, attempt);
          } else activities.interact(learner, ContentStore.id(activity, "id"), null, true);
        }
      }
      journey.complete(learner, destination);
      journey.complete(learner, destination);
    }
    assertThat(journey.journey(learner, null, null).completedStops()).isEqualTo(3);
    assertThat(journey.passport(learner)).hasSize(3).allSatisfy(stamp -> assertThat(stamp.unlocked()).isTrue());
    assertThat(db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND passed=true", learner)).isEqualTo(7);
  }

  @Test void seedPreservesAnExistingJourneyAndDoesNotDuplicateOnRerun() throws Exception {
    long manager = user("CONTENT_MANAGER"), admin = user("ADMIN");
    long original = destinations.create(new DestinationRequest("Điểm đang biên soạn", "Existing draft", "existing-demo-test",
        VietnamRegion.NORTH, null, null, null, null, null, DestinationStatus.DRAFT)).id();
    journey.configure(new JourneyRequest(List.of(new JourneyRequest.Stop(original, DestinationStatus.DRAFT))));
    long course = seed.seed(manager, admin);
    assertThat(seed.seed(manager, admin)).isEqualTo(course);
    assertThat(journey.configuration().stops()).hasSize(4);
    assertThat(journey.configuration().stops().getFirst().destination().id()).isEqualTo(original);
  }
}
