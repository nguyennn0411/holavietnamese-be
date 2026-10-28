package com.sep490.backend.seed;

import com.sep490.backend.service.*;
import com.sep490.backend.dto.request.ContentRequests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.service.CourseContentService;
import com.sep490.backend.service.GrammarService;
import com.sep490.backend.service.ActivityService;
import com.sep490.backend.service.QuestionBankService;
import com.sep490.backend.service.QuizService;
import com.sep490.backend.dto.request.ContentRequests;
import com.sep490.backend.service.ContentStore;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the four-level Tiếng Việt 123 learning path through the production content services. */
@Service
@RequiredArgsConstructor
public class TiengViet123CourseSeed {
  public static final List<String> CODES = List.of("TV123-A0", "TV123-A1-1", "TV123-A1-2", "TV123-A2");

  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final GrammarService grammar;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Long> seed(long author) throws Exception {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", author);
    JsonNode root;
    try (var in = new ClassPathResource("learning/tv123-courses.json").getInputStream()) {
      root = json.readTree(in);
    }
    validate(root);
    Map<String, Long> ids = new LinkedHashMap<>();
    for (JsonNode data : root.path("courses")) {
      String code = data.path("course").path("code").asText();
      var existing = db.rows("SELECT id FROM courses WHERE code=?", code);
      if (!existing.isEmpty()) {
        long courseId = ContentStore.id(existing.getFirst(), "id");
        syncIllustrations(data, courseId);
        ids.put(code, courseId);
        continue;
      }
      ids.put(code, createCourse(data, author));
    }
    return ids;
  }

  /** Keeps newly added textbook media in sync without recreating learner progress. */
  private void syncIllustrations(JsonNode data, long courseId) {
    String courseSlug = data.path("course").path("slug").asText();
    for (JsonNode module : data.path("modules")) {
      for (JsonNode lesson : module.path("lessons")) {
        JsonNode illustrations = null;
        for (JsonNode activity : lesson.path("activities")) {
          if ("TEXT".equals(activity.path("activityType").asText())
              && activity.path("contentJson").path("illustrations").isArray()) {
            illustrations = activity.path("contentJson").path("illustrations");
            break;
          }
        }
        if (illustrations == null) continue;
        String suffix = String.format("%02d", lesson.path("number").asInt());
        var lessonRows = db.rows(
            "SELECT id FROM lessons WHERE course_id=? AND slug=?",
            courseId,
            courseSlug + "-bai-" + suffix);
        if (lessonRows.isEmpty()) continue;
        long lessonId = ContentStore.id(lessonRows.getFirst(), "id");
        var textActivities = db.rows(
            "SELECT id,content_json FROM lesson_activities"
                + " WHERE lesson_id=? AND activity_type='TEXT' ORDER BY order_index LIMIT 1",
            lessonId);
        if (textActivities.isEmpty()) continue;
        var row = textActivities.getFirst();
        ObjectNode content = db.json(row.get("contentJson")).deepCopy();
        content.set("illustrations", illustrations.deepCopy());
        db.update(
            "UPDATE lesson_activities SET content_json=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            db.encode(content),
            ContentStore.id(row, "id"));
      }
    }
  }

  private long createCourse(JsonNode data, long author) throws Exception {
    ContentRequests.Course request = json.treeToValue(data.path("course"), ContentRequests.Course.class);
    long courseId = courses.create(request, author);
    Long previousLesson = null;
    List<Long> finalQuestions = new ArrayList<>();
    int moduleNumber = 0;
    for (JsonNode moduleData : data.path("modules")) {
      moduleNumber++;
      long moduleId = courses.addModule(courseId,
          json.treeToValue(moduleData.path("metadata"), ContentRequests.Module.class));
      List<List<Long>> moduleQuestions = new ArrayList<>();
      long lastLesson = 0;
      for (JsonNode lessonData : moduleData.path("lessons")) {
        int number = lessonData.path("number").asInt();
        String suffix = String.format("%02d", number);
        long lessonId = courses.addLesson(moduleId, new ContentRequests.Lesson(
            request.code() + "-L" + suffix,
            request.slug() + "-bai-" + suffix,
            lessonData.path("titleEn").asText(),
            lessonData.path("descriptionEn").asText(),
            "NORMAL",
            lessonData.path("estimatedMinutes").asInt(30),
            previousLesson == null ? List.of() : List.of(previousLesson),
            lessonData.path("objectiveEn").asText(),
            lessonData.path("titleVi").asText(),
            lessonData.path("descriptionVi").asText(),
            lessonData.path("objectiveVi").asText()));
        previousLesson = lessonId;
        lastLesson = lessonId;

        for (JsonNode activityData : lessonData.path("activities")) {
          ObjectNode payload = activityData.deepCopy();
          payload.put("isRequired", true).put("maxScore", 0).put("status", "PUBLISHED");
          activities.create(lessonId, json.treeToValue(payload, ContentRequests.Activity.class));
        }
        if (lessonData.has("grammar")) {
          ObjectNode grammarData = lessonData.path("grammar").deepCopy();
          grammarData.put("code", request.code() + "-G" + suffix)
              .put("slug", request.slug() + "-ngu-phap-" + suffix)
              .put("level", request.level());
          long grammarId = grammar.create(json.treeToValue(grammarData, ContentRequests.Grammar.class));
          grammar.status(grammarId, "PUBLISHED");
          activities.create(lessonId, new ContentRequests.Activity(
              "GRAMMAR", "Grammar and usage", "Study the pattern and say each example aloud.",
              json.createObjectNode(), true, BigDecimal.ZERO, "PUBLISHED", null, List.of(grammarId),
              "Ngữ pháp và cách dùng", "Học mẫu câu và đọc to từng ví dụ."));
        }

        long lessonQuiz = quiz(courseId, lessonId, false,
            "Kiểm tra: " + lessonData.path("titleVi").asText(),
            "Quiz: " + lessonData.path("titleEn").asText());
        List<Long> lessonQuestions = new ArrayList<>();
        for (JsonNode questionData : lessonData.path("questions")) {
          long questionId = questions.create(json.treeToValue(questionData, ContentRequests.Question.class));
          quizzes.addQuestion(lessonQuiz, new ContentRequests.QuizQuestion(questionId, BigDecimal.ONE));
          lessonQuestions.add(questionId);
          finalQuestions.add(questionId);
        }
        moduleQuestions.add(lessonQuestions);
        publishQuizActivity(lessonId, lessonQuiz, "Kiểm tra cuối bài", "Lesson quiz");
        courses.lessonStatus(lessonId, "PUBLISHED");
      }

      long checkpoint = quiz(courseId, lastLesson, false,
          "Checkpoint học phần " + moduleNumber, "Module " + moduleNumber + " checkpoint");
      for (List<Long> lessonQuestionIds : moduleQuestions) {
        for (long questionId : lessonQuestionIds.subList(0, Math.min(2, lessonQuestionIds.size()))) {
          quizzes.addQuestion(checkpoint, new ContentRequests.QuizQuestion(questionId, BigDecimal.ONE));
        }
      }
      publishQuizActivity(lastLesson, checkpoint, "Checkpoint học phần", "Module checkpoint");
    }

    long finalQuiz = quiz(courseId, previousLesson, true,
        "Đánh giá cuối khóa", "Final course assessment");
    for (long questionId : finalQuestions) {
      quizzes.addQuestion(finalQuiz, new ContentRequests.QuizQuestion(questionId, BigDecimal.ONE));
    }
    publishQuizActivity(previousLesson, finalQuiz, "Đánh giá cuối khóa", "Final course assessment");
    db.update("UPDATE courses SET final_assessment_quiz_id=? WHERE id=?", finalQuiz, courseId);
    courses.status(courseId, "PUBLISHED");
    return courseId;
  }

  private long quiz(long courseId, long lessonId, boolean isFinal, String titleVi, String titleEn) {
    return quizzes.create(new ContentRequests.Quiz(
        titleEn,
        "Pass with at least 70%. Review the lesson explanations before retrying.",
        isFinal ? "COURSE" : "LESSON",
        courseId,
        lessonId,
        BigDecimal.valueOf(70),
        null,
        null,
        false,
        titleVi,
        "Đạt từ 70%. Xem lại giải thích trong bài trước khi làm lại."));
  }

  private void publishQuizActivity(long lessonId, long quizId, String titleVi, String titleEn) {
    quizzes.status(quizId, "PUBLISHED");
    activities.create(lessonId, new ContentRequests.Activity(
        "QUIZ", titleEn, null, json.createObjectNode(), true, BigDecimal.ZERO,
        "PUBLISHED", quizId, List.of(), titleVi, null));
  }

  private void validate(JsonNode root) throws Exception {
    if (root.path("courses").size() != 4) throw new IllegalStateException("Expected four TV123 courses");
    int lessonCount = 0;
    for (JsonNode courseData : root.path("courses")) {
      String code = courseData.path("course").path("code").asText();
      if (!CODES.contains(code)) throw new IllegalStateException("Unexpected course code " + code);
      for (JsonNode module : courseData.path("modules")) {
        for (JsonNode lesson : module.path("lessons")) {
          lessonCount++;
          if (lesson.path("questions").size() < 4 || lesson.path("activities").size() < 3) {
            throw new IllegalStateException("Incomplete lesson in " + code);
          }
          validateMedia(lesson);
        }
      }
    }
    if (lessonCount != 20) throw new IllegalStateException("Expected 20 lessons, found " + lessonCount);
  }

  private void validateMedia(JsonNode node) throws Exception {
    if (node.isObject()) {
      if (node.hasNonNull("imageUrl")) {
        String image = node.path("imageUrl").asText();
        if (!image.startsWith("/media/tv123/images/")
            || !new ClassPathResource("static" + image).exists()) {
          throw new IllegalStateException("Missing TV123 image " + image);
        }
      }
      if (node.hasNonNull("audioUrl")) {
        URI uri = URI.create(node.path("audioUrl").asText());
        if (!"https".equals(uri.getScheme()) || !"123vietnamese.com".equals(uri.getHost())) {
          throw new IllegalStateException("Unexpected TV123 audio source " + uri);
        }
      }
    }
    if (node.isContainerNode()) for (JsonNode child : node) validateMedia(child);
  }
}
