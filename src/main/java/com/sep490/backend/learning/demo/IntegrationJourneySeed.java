package com.sep490.backend.learning.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.request.*;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.course.CourseReviewService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.QuestionBankService;
import com.sep490.backend.learning.quiz.QuizService;
import com.sep490.backend.learning.shared.ContentRequests;
import com.sep490.backend.learning.shared.ContentStore;
import com.sep490.backend.service.DestinationService;
import com.sep490.backend.service.JourneyService;
import com.sep490.backend.service.VocabularyWordService;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A repeatable, opt-in fixture connecting the three modules through real persisted IDs. */
@Service
@RequiredArgsConstructor
public class IntegrationJourneySeed {
  public static final String CODE = "DEMO-VIETNAM-LINKS";
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final CourseReviewService review;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;
  private final VocabularyWordService vocabulary;
  private final DestinationService destinations;
  private final JourneyService journey;

  @Transactional(rollbackFor = Exception.class)
  public long seed(long author, long reviewer) throws Exception {
    db.one("SELECT id FROM users WHERE id=? AND role='CONTENT_MANAGER' FOR UPDATE", author);
    db.one("SELECT id FROM users WHERE id=? AND role='ADMIN'", reviewer);
    var existing = db.rows("SELECT id FROM courses WHERE code=?", CODE);
    if (!existing.isEmpty()) {
      long id = ContentStore.id(existing.getFirst(), "id");
      if (db.count("SELECT count(*) FROM lessons WHERE course_id=?", id) != 6
          || db.count("SELECT count(*) FROM destination_lessons dl JOIN lessons l ON l.id=dl.lesson_id WHERE l.course_id=?", id) != 6
          || db.count("SELECT count(*) FROM destination_vocabularies dv JOIN destinations d ON d.id=dv.destination_id WHERE d.slug LIKE 'demo-links-%'") != 18
          || db.count("SELECT count(*) FROM quizzes WHERE course_id=?", id) != 7)
        throw new IllegalStateException("Integration demo exists with a different structure; existing content was preserved.");
      return id;
    }
    JsonNode source;
    try (var input = new ClassPathResource("demo/integration-journey.json").getInputStream()) {
      source = json.readTree(input);
    }
    long course = courses.create(json.treeToValue(source.path("course"), ContentRequests.Course.class), author);
    List<Long> finalQuestions = new ArrayList<>();
    List<JourneyRequest.Stop> stops = new ArrayList<>();
    // Preserve all existing journey stops and append the demo rather than replacing configuration.
    for (var stop : journey.configuration().stops())
      stops.add(new JourneyRequest.Stop(stop.destination().id(), DestinationStatus.valueOf(stop.status())));
    Long previous = null;
    int number = 0;
    for (var city : source.path("stops")) {
      String slug = city.path("destination").path("slug").asText();
      if (db.count("SELECT count(*) FROM destinations WHERE slug=?", slug) > 0)
        throw new IllegalStateException("Reserved demo slug already exists: " + slug);
      long destination = destinations.create(json.treeToValue(city.path("destination"), DestinationRequest.class)).id();
      long module = courses.addModule(course, json.treeToValue(city.path("module"), ContentRequests.Module.class));
      List<Long> lessonIds = new ArrayList<>();
      List<Long> wordIds = new ArrayList<>();
      for (var lessonData : city.path("lessons")) {
        number++;
        long lesson = courses.addLesson(module, new ContentRequests.Lesson(
            CODE + "-L" + number, "demo-vietnam-links-" + number,
            lessonData.path("titleEn").asText(), lessonData.path("descriptionEn").asText(lessonData.path("textEn").asText()), "NORMAL", lessonData.path("estimatedMinutes").asInt(20),
            previous == null ? List.of() : List.of(previous), lessonData.path("objectiveEn").asText(),
            lessonData.path("titleVi").asText(), lessonData.path("descriptionVi").asText(lessonData.path("textVi").asText()), lessonData.path("objectiveVi").asText()));
        previous = lesson;
        lessonIds.add(lesson);
        activity(lesson, "TEXT", "Khám phá địa phương và thực hành giao tiếp", "Local culture and communication practice",
            json.createObjectNode().put("textVi", lessonData.path("textVi").asText()).put("textEn", lessonData.path("textEn").asText()), null);
        var content = json.createObjectNode();
        var items = content.putArray("items");
        var ids = content.putArray("vocabularyIds");
        for (var word : lessonData.path("words")) {
          long wordId = word(word);
          wordIds.add(wordId);
          ids.add(wordId);
          var item = word.deepCopy();
          ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("masterVocabularyId", wordId);
          items.add(item);
        }
        activity(lesson, "VOCABULARY", "Từ vựng dùng chung với điểm đến", "Vocabulary shared with the destination", content, null);
        activity(lesson, "DIALOGUE", "Hội thoại thực hành", "Practice dialogue",
            json.createObjectNode().set("lines", lessonData.path("dialogue")), null);
        long quiz = quiz(course, lesson, false);
        int index = 0;
        for (var question : lessonData.path("questions")) {
          long questionId = questions.create(json.treeToValue(question, ContentRequests.Question.class), author);
          quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(questionId, BigDecimal.ONE));
          if (index++ == 0) finalQuestions.add(questionId);
        }
        quizzes.status(quiz, "PUBLISHED");
        activity(lesson, "QUIZ", "Kiểm tra cuối bài", "Lesson quiz", json.createObjectNode(), quiz);
        courses.lessonStatus(lesson, "PUBLISHED");
      }
      journey.link(destination, new DestinationContentRequest(lessonIds, wordIds));
      stops.add(new JourneyRequest.Stop(destination, DestinationStatus.PUBLISHED));
    }
    if (number != 6 || finalQuestions.size() != 6) throw new IllegalStateException("Expected six complete demo lessons");
    long finalQuiz = quiz(course, previous, true);
    for (long question : finalQuestions) quizzes.addQuestion(finalQuiz, new ContentRequests.QuizQuestion(question, BigDecimal.ONE));
    quizzes.status(finalQuiz, "PUBLISHED");
    activity(previous, "QUIZ", "Kiểm tra cuối hành trình", "Final journey assessment", json.createObjectNode(), finalQuiz);
    courses.setFinalAssessment(course, finalQuiz);
    var manager = new LearnerPrincipal(author, "integration-author", "", true, "CONTENT_MANAGER");
    var admin = new LearnerPrincipal(reviewer, "integration-reviewer", "", true, "ADMIN");
    review.submit(course, manager);
    review.review(course, "APPROVE", "Dữ liệu mẫu tích hợp bài học, từ vựng và hành trình.", admin);
    journey.configure(new JourneyRequest(stops));
    return course;
  }

  private long word(JsonNode word) {
    var existing = db.rows("SELECT id,status FROM vocabularies WHERE LOWER(word)=LOWER(?) AND LOWER(part_of_speech)=LOWER(?)",
        word.path("wordVi").asText(), word.path("wordType").asText());
    if (!existing.isEmpty()) {
      if (!"PUBLISHED".equals(existing.getFirst().get("status")))
        throw new IllegalStateException("Demo word already exists but is not published: " + word.path("wordVi").asText());
      long id = ContentStore.id(existing.getFirst(), "id");
      if (db.count("SELECT count(*) FROM vocabulary_meanings WHERE vocabulary_id=? AND translation_en=?", id, word.path("meaningEn").asText()) == 0)
        throw new IllegalStateException("Existing vocabulary has a different meaning; preserved: " + word.path("wordVi").asText());
      return id;
    }
    var example = new VocabularyExampleRequest(word.path("exampleVi").asText(), word.path("exampleEn").asText(), null, 0);
    var meaning = new VocabularyMeaningRequest(word.path("meaningEn").asText(), null,
        "Ví dụ trong khóa học Hành trình Việt Nam — Demo liên kết", 0, List.of(example));
    return vocabulary.create(new VocabularyWordRequest(word.path("wordVi").asText(), null,
        word.path("wordType").asText(), "A1", null, VocabularyStatus.PUBLISHED, Set.of(), List.of(meaning))).id();
  }

  private long quiz(long course, long lesson, boolean last) {
    return quizzes.create(new ContentRequests.Quiz(last ? "Final journey assessment" : "Lesson assessment",
        "Answer at least 70% correctly. You can retry.", last ? "COURSE" : "LESSON", course, lesson,
        BigDecimal.valueOf(70), null, null, false,
        last ? "Kiểm tra cuối hành trình" : "Kiểm tra cuối bài", "Đạt tối thiểu 70%. Có thể làm lại."));
  }

  private void activity(long lesson, String type, String vi, String en, JsonNode content, Long quiz) {
    activities.create(lesson, new ContentRequests.Activity(type, en, null, content, true,
        BigDecimal.ZERO, "PUBLISHED", quiz, List.of(), vi, null));
  }
}
