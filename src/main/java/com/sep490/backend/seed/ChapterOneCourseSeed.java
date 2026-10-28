package com.sep490.backend.seed;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.service.CourseContentService;
import com.sep490.backend.service.ActivityService;
import com.sep490.backend.service.*;
import com.sep490.backend.dto.request.ContentRequests;
import com.sep490.backend.dto.response.learning.PageResponse;
import com.sep490.backend.exception.ContentException;
import com.sep490.backend.service.ContentList;
import com.sep490.backend.service.ContentRules;
import com.sep490.backend.service.ContentStore;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in, transactional authoring of the user's standalone A0 pronunciation course. */
@Service
@RequiredArgsConstructor
public class ChapterOneCourseSeed {
  public static final String CODE = "VI-PRON-CH01";
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional(rollbackFor = Exception.class)
  public long seed(long author) throws Exception {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", author);
    var existing = db.rows("SELECT id FROM courses WHERE code=?", CODE);
    if (!existing.isEmpty()) {
      long id = ContentStore.id(existing.getFirst(), "id");
      if (db.count("SELECT count(*) FROM course_units WHERE course_id=?", id) != 8
          || db.count("SELECT count(*) FROM lessons WHERE course_id=?", id) != 34
          || db.count("SELECT count(*) FROM quizzes WHERE course_id=?", id) != 43)
        throw new IllegalStateException("Existing pronunciation course differs; migrate explicitly to preserve learner history.");
      return id;
    }
    JsonNode data;
    try (var in = new ClassPathResource("learning/chapter-one-course.json").getInputStream()) {
      data = json.readTree(in);
    }
    validate(data);
    long course = courses.create(json.treeToValue(data.get("course"), ContentRequests.Course.class), author);
    Long previous = null;
    int moduleNumber = 0;
    for (var moduleData : data.path("modules")) {
      ++moduleNumber;
      long module = courses.addModule(course, json.treeToValue(moduleData.get("metadata"), ContentRequests.Module.class));
      List<List<Long>> moduleQuestions = new ArrayList<>();
      long last = 0;
      for (var lessonData : moduleData.path("lessons")) {
        int n = lessonData.path("number").asInt();
        long lesson = courses.addLesson(module, new ContentRequests.Lesson(CODE + "-L" + n, "chapter-one-lesson-" + n,
            lessonData.path("titleEn").asText(), lessonData.path("descriptionEn").asText(), n == 34 ? "REVIEW" : "NORMAL",
            lessonData.path("estimatedMinutes").asInt(), List.of(),
            lessonData.path("objectiveEn").asText(), lessonData.path("titleVi").asText(), lessonData.path("descriptionVi").asText(), lessonData.path("objectiveVi").asText()));
        previous = lesson;
        last = lesson;
        for (var activity : lessonData.path("activities")) {
          ObjectNode payload = activity.deepCopy();
          payload.put("isRequired", true).put("maxScore", 0).put("status", "PUBLISHED");
          activities.create(lesson, json.treeToValue(payload, ContentRequests.Activity.class));
        }
        long quiz = quiz(course, lesson, false, "Kiểm tra: " + lessonData.path("titleVi").asText(), "Quiz: " + lessonData.path("titleEn").asText());
        List<Long> ids = new ArrayList<>();
        for (var q : lessonData.path("questions")) ids.add(addQuestion(quiz, q));
        moduleQuestions.add(ids);
        publishQuiz(lesson, quiz, "Kiểm tra cuối bài", "Lesson quiz");
        courses.lessonStatus(lesson, "PUBLISHED");
      }
      long checkpoint = quiz(course, last, false, "Checkpoint module " + moduleNumber, "Module " + moduleNumber + " checkpoint");
      // Two items per lesson cover every lesson; single-lesson modules reuse the full set.
      for (var ids : moduleQuestions)
        for (long id : ids.subList(0, moduleQuestions.size() == 1 ? ids.size() : Math.min(2, ids.size())))
          quizzes.addQuestion(checkpoint, new ContentRequests.QuizQuestion(id, BigDecimal.ONE));
      publishQuiz(last, checkpoint, moduleNumber == 2 ? "Checkpoint thanh điệu bắt buộc" : "Kiểm tra cuối module",
          moduleNumber == 2 ? "Required tone checkpoint" : "Module checkpoint");
    }
    long finalQuiz = quiz(course, previous, true, "Đánh giá cuối khóa: 50 câu", "Final assessment: 50 questions");
    for (var q : data.path("finalQuestions")) addQuestion(finalQuiz, q);
    publishQuiz(previous, finalQuiz, "Kiểm tra cuối khóa", "Final course assessment");
    db.update("UPDATE courses SET final_assessment_quiz_id=? WHERE id=?", finalQuiz, course);
    courses.status(course, "PUBLISHED");
    return course;
  }

  private long addQuestion(long quiz, JsonNode q) throws Exception {
    ObjectNode payload = q.deepCopy();
    payload.remove("points");
    long id = questions.create(json.treeToValue(payload, ContentRequests.Question.class));
    quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(id, BigDecimal.valueOf(q.path("points").asInt(1))));
    return id;
  }

  private long quiz(long course, long lesson, boolean isFinal, String vi, String en) {
    return quizzes.create(new ContentRequests.Quiz(en,
        "Pass at 70%. Retry as needed. Review the explanations; for hỏi/ngã difficulties revisit lessons 3–7.",
        isFinal ? "COURSE" : "LESSON", course, lesson, BigDecimal.valueOf(70), null, null, false, vi,
        "Đạt từ 70%. Có thể làm lại. Xem giải thích khi sai; nếu nhầm hỏi/ngã, ôn bài 3–7."));
  }

  private void publishQuiz(long lesson, long quiz, String vi, String en) {
    quizzes.status(quiz, "PUBLISHED");
    activities.create(lesson, new ContentRequests.Activity("QUIZ", en, null, json.createObjectNode(), true,
        BigDecimal.ZERO, "PUBLISHED", quiz, List.of(), vi, null));
  }

  private void validate(JsonNode data) throws Exception {
    if (data.path("modules").size() != 8 || data.path("finalQuestions").size() != 50)
      throw new IllegalStateException("Expected 8 modules and 50 final questions");
    int count = 0;
    for (var m : data.path("modules")) for (var l : m.path("lessons")) {
      if (l.path("number").asInt() != ++count || l.path("questions").size() < 4)
        throw new IllegalStateException("Incomplete or unordered lesson");
      for (String key : List.of("titleVi", "titleEn", "descriptionVi", "descriptionEn"))
        if (l.path(key).asText().isBlank()) throw new IllegalStateException("Missing " + key);
    }
    if (count != 34) throw new IllegalStateException("Expected all 34 authored lessons");
    if (data.path("coverage").path("vowels").size() != 12
        || data.path("coverage").path("consonants").size() != 17
        || data.path("coverage").path("vowelCombinations").size() != 35)
      throw new IllegalStateException("Incomplete Chapter 1 coverage");
    Map<String, Integer> totals = new HashMap<>();
    for (var q : data.path("finalQuestions")) totals.merge(q.path("topic").asText(), q.path("points").asInt(), Integer::sum);
    if (!totals.equals(Map.of("CH01_MODULE_1", 8, "CH01_MODULE_2", 20, "CH01_MODULE_3", 14, "CH01_MODULE_4", 30, "CH01_MODULE_5", 10, "CH01_MODULE_6", 8, "CH01_MODULE_7", 6, "CH01_MODULE_8", 4)))
      throw new IllegalStateException("Final assessment weights differ from the outline");
    validateAudio(data);
  }

  private void validateAudio(JsonNode node) throws Exception {
    if (node.isObject() && node.hasNonNull("audioUrl")) {
      String url = node.path("audioUrl").asText();
      if (!url.startsWith("/media/pronunciation/")) throw new IllegalStateException("Unexpected audio path");
      try (var in = new ClassPathResource("static" + url).getInputStream()) {
        byte[] bytes = in.readNBytes(12);
        if (bytes.length != 12 || !new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
            || !new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE"))
          throw new IllegalStateException("Invalid WAV: " + url);
      }
    }
    if (node.isContainerNode()) for (var child : node) validateAudio(child);
  }
}
