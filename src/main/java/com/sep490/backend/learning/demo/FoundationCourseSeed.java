package com.sep490.backend.learning.demo;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.grammar.GrammarService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.*;
import com.sep490.backend.learning.shared.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional curriculum authoring seed. All learner reads use the database, never this file. */
@Service
@RequiredArgsConstructor
public class FoundationCourseSeed {
  public static final String CODE = "VI-A0-A1-FOUNDATION";
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final GrammarService grammar;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional(rollbackFor = Exception.class)
  public long seed(long author) throws Exception {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", author);
    var existing = db.rows("SELECT id FROM courses WHERE code=?", CODE);
    if (!existing.isEmpty()) {
      long id = ContentStore.id(existing.getFirst(), "id");
      if (db.count("SELECT count(*) FROM lessons WHERE course_id=?", id) != 48
          || db.count("SELECT count(*) FROM course_units WHERE course_id=?", id) != 10)
        throw new IllegalStateException("Foundation course already exists with a different structure; migrate explicitly to preserve progress.");
      return id;
    }
    JsonNode data;
    try (var in = new ClassPathResource("learning/foundation-course.json").getInputStream()) {
      data = json.readTree(in);
    }
    validate(data);
    long course = courses.create(json.treeToValue(data.get("course"), ContentRequests.Course.class), author);
    Map<Integer, Long> lessonIds = new LinkedHashMap<>();
    Map<Integer, List<Long>> questionIds = new LinkedHashMap<>();
    Long previous = null;
    for (var m : data.path("modules")) {
      long module = courses.addModule(course, json.treeToValue(m.get("metadata"), ContentRequests.Module.class));
      for (var l : m.path("lessons")) {
        int n = l.path("number").asInt();
        String code = CODE + "-L" + n, slug = "foundation-lesson-" + n;
        long lesson = courses.addLesson(module, new ContentRequests.Lesson(code, slug,
            l.path("titleEn").asText(), l.path("descriptionEn").asText(), n == 48 ? "REVIEW" : "NORMAL",
            l.path("estimatedMinutes").asInt(), previous == null ? List.of() : List.of(previous),
            l.path("objectiveEn").asText(), l.path("titleVi").asText(), l.path("descriptionVi").asText(), l.path("objectiveVi").asText()));
        previous = lesson;
        lessonIds.put(n, lesson);
        activity(lesson, "TEXT", "Giới thiệu", "Introduction", l.get("introduction"), null, List.of());
        activity(lesson, "VOCABULARY", "Từ vựng trong ngữ cảnh", "Vocabulary in context",
            json.createObjectNode().set("items", l.get("vocabulary")), null, List.of());
        var pattern = l.path("pattern");
        if (pattern.path("type").asText().equals("GRAMMAR")) {
          List<ContentRequests.Example> examples = new ArrayList<>();
          for (var line : l.path("lines")) examples.add(new ContentRequests.Example(line.path("textVi").asText(), line.path("textEn").asText(), null));
          long topic = grammar.create(new ContentRequests.Grammar(code + "-G", slug + "-pattern",
              l.path("titleEn").asText(), pattern.path("structureVi").asText(), pattern.path("textEn").asText(),
              null, "A1", examples, null, null, l.path("titleVi").asText(), null,
              pattern.path("textVi").asText(), null, pattern.path("structureEn").asText()));
          grammar.status(topic, "PUBLISHED");
          activity(lesson, "GRAMMAR", "Cách dùng và mẫu câu", "Usage and sentence patterns", json.createObjectNode(), null, List.of(topic));
        } else activity(lesson, "SOUND_PATTERN", "Âm, vần và cách phát âm", "Sounds, rimes and pronunciation", pattern, null, List.of());
        activity(lesson, "AUDIO", "Nghe và đọc theo", "Listen and repeat", l.get("audio"), null, List.of());
        activity(lesson, "DIALOGUE", n == 48 ? "Đọc hiểu tổng hợp" : "Ví dụ trong ngữ cảnh",
            n == 48 ? "Integrated reading" : "Examples in context", json.createObjectNode().set("lines", l.get("lines")), null, List.of());
        activity(lesson, "PRACTICE", "Ghép từ vào câu", "Match words to sentences", l.get("practice"), null, List.of());
        long quiz = quiz(course, lesson, n == 48 ? "COURSE" : "LESSON", "Kiểm tra: " + l.path("titleVi").asText(), "Assessment: " + l.path("titleEn").asText());
        var ids = new ArrayList<Long>();
        for (var q : l.path("questions")) {
          ObjectNode payload = q.deepCopy();
          payload.remove("points");
          long question = questions.create(json.treeToValue(payload, ContentRequests.Question.class));
          ids.add(question);
          quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(question, BigDecimal.valueOf(q.path("points").asInt(1))));
        }
        questionIds.put(n, ids);
        quizzes.status(quiz, "PUBLISHED");
        activity(lesson, "QUIZ", n == 48 ? "Đánh giá cuối khóa" : "Kiểm tra cuối bài",
            n == 48 ? "Final course assessment" : "Lesson quiz", json.createObjectNode(), quiz, List.of());
        if (n == 48) db.update("UPDATE courses SET final_assessment_quiz_id=? WHERE id=?", quiz, course);
        courses.lessonStatus(lesson, "PUBLISHED");
      }
    }
    for (var checkpoint : data.path("checkpoints")) {
      int first = checkpoint.path("fromLesson").asInt(), last = checkpoint.path("afterLesson").asInt(), count = checkpoint.path("count").asInt();
      long lesson = lessonIds.get(last);
      long quiz = quiz(course, lesson, "LESSON", "Ôn tập bài " + first + "–" + last, "Review lessons " + first + "–" + last);
      // Round-robin selection covers the entire module rather than just its first few lessons.
      List<Long> selected = new ArrayList<>();
      for (int index = 0; selected.size() < count; index++) {
        for (int n = first; n <= last && selected.size() < count; n++) {
          var ids = questionIds.get(n);
          if (index < ids.size()) selected.add(ids.get(index));
        }
        if (index > 20) throw new IllegalStateException("Insufficient checkpoint questions");
      }
      // Include genuine listening and matching in each checkpoint, with server-side keys.
      selected.set(count - 2, questionIds.get(last).getLast());
      var authored = findLesson(data, last).path("practice");
      long matching = questions.create(new ContentRequests.Question("MATCHING", authored.path("promptEn").asText(),
          "Use the vocabulary sentences to check each pairing.", "EASY", "CHECKPOINT", null, null, "PUBLISHED",
          authored.path("correctAnswer"), authored.path("options"), authored.path("promptVi").asText(), "Đối chiếu từng cặp với câu trong phần từ vựng."));
      selected.set(count - 1, matching);
      if (new HashSet<>(selected).size() != count) throw new IllegalStateException("Duplicate checkpoint item");
      for (long question : selected) quizzes.addQuestion(quiz, new ContentRequests.QuizQuestion(question, BigDecimal.ONE));
      quizzes.status(quiz, "PUBLISHED");
      activity(lesson, "QUIZ", "Kiểm tra tổng hợp âm và từ", "Sound and vocabulary checkpoint", json.createObjectNode(), quiz, List.of());
    }
    courses.status(course, "PUBLISHED");
    return course;
  }

  private JsonNode findLesson(JsonNode data, int number) {
    for (var m : data.path("modules")) for (var l : m.path("lessons")) if (l.path("number").asInt() == number) return l;
    throw new IllegalStateException("Missing lesson " + number);
  }

  private void validate(JsonNode data) {
    if (data.path("modules").size() != 10) throw new IllegalStateException("Expected 10 modules");
    int count = 0;
    for (var m : data.path("modules")) for (var l : m.path("lessons")) {
      if (l.path("number").asInt() != ++count) throw new IllegalStateException("Lesson sequence mismatch");
      for (String key : List.of("titleVi", "titleEn", "descriptionVi", "descriptionEn"))
        if (l.path(key).asText().isBlank()) throw new IllegalStateException("Missing bilingual lesson field");
      if (l.path("vocabulary").size() < 5 || l.path("questions").size() < 5 || l.path("lines").size() < 3)
        throw new IllegalStateException("Incomplete lesson " + count);
      for (var q : l.path("questions")) if (q.hasNonNull("audioUrl")) requireAudio(q.path("audioUrl").asText());
      requireAudio(l.path("audio").path("audioUrl").asText());
    }
    if (count != 48) throw new IllegalStateException("Expected 48 lessons");
  }

  private void requireAudio(String url) {
    if (!url.startsWith("/media/foundation/") || !new ClassPathResource("static" + url).exists())
      throw new IllegalStateException("Missing curriculum audio " + url);
  }

  private long quiz(long course, long lesson, String type, String vi, String en) {
    return quizzes.create(new ContentRequests.Quiz(en, "Score at least 70%. You may retry; completed activities are retained.",
        type, course, lesson, BigDecimal.valueOf(70), null, null, false, vi,
        "Đạt từ 70%. Bạn có thể làm lại; các hoạt động đã hoàn thành được giữ lại."));
  }

  private void activity(long lesson, String type, String vi, String en, JsonNode content, Long quiz, List<Long> topics) {
    activities.create(lesson, new ContentRequests.Activity(type, en, "Read, listen or respond, then continue.", content, true,
        BigDecimal.ZERO, "PUBLISHED", quiz, topics, vi, "Đọc, nghe hoặc trả lời rồi tiếp tục."));
  }
}
