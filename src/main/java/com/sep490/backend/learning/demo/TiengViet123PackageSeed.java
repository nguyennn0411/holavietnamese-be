package com.sep490.backend.learning.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.QuestionBankService;
import com.sep490.backend.learning.quiz.QuizService;
import com.sep490.backend.learning.shared.ContentRequests;
import com.sep490.backend.learning.shared.ContentStore;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Imports the separately supplied 88-lesson TV123 package without changing existing courses. */
@Service
@RequiredArgsConstructor
public class TiengViet123PackageSeed {
  public static final List<String> CODES =
      List.of("TV123-C00", "TV123-C01", "TV123-C02", "TV123-C03");
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional(rollbackFor = Exception.class)
  public Map<String, Long> seed(long author) throws Exception {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", author);
    JsonNode root;
    try (var in = new ClassPathResource("learning/tv123-package-courses.json").getInputStream()) {
      root = json.readTree(in);
    }
    if (root.path("courses").size() != 4)
      throw new IllegalStateException("Expected four package courses");
    Map<String, Long> result = new LinkedHashMap<>();
    for (JsonNode source : root.path("courses")) {
      String code = source.path("id").asText();
      if (!CODES.contains(code) || result.containsKey(code))
        throw new IllegalStateException("Invalid package course " + code);
      var existing = db.rows("SELECT id FROM courses WHERE code=?", code);
      if (!existing.isEmpty()) {
        long id = ContentStore.id(existing.getFirst(), "id");
        long expected = 0;
        for (JsonNode module : source.path("modules")) expected += module.path("lessons").size();
        if (db.count("SELECT count(*) FROM lessons WHERE course_id=?", id) != expected)
          throw new IllegalStateException(
              "Course " + code + " exists with a different lesson count");
        result.put(code, id);
      } else result.put(code, create(source, author));
    }
    return result;
  }

  private long create(JsonNode source, long author) {
    String code = source.path("id").asText();
    String level = code.equals("TV123-C00") ? "A0" : code.equals("TV123-C03") ? "A2" : "A1";
    String slug = "tieng-viet-123-package-" + code.substring(6).toLowerCase(Locale.ROOT);
    String titleVi = source.path("title").asText();
    String titleEn = source.path("en").asText(titleVi);
    String description = source.path("description").asText();
    long course =
        courses.create(
            new ContentRequests.Course(
                code,
                slug,
                titleEn,
                description,
                level,
                null,
                source.path("estimated_minutes").asInt(),
                "Phân cấp nội bộ; không tương đương chứng chỉ CEFR hoặc Khung năng lực tiếng Việt 6"
                    + " bậc.",
                true,
                titleVi,
                description),
            author);
    Long previous = null;
    Long finalQuiz = null;
    for (JsonNode moduleData : source.path("modules")) {
      String moduleVi = moduleData.path("title_vi").asText();
      long module =
          courses.addModule(
              course,
              new ContentRequests.Module(
                  moduleData.path("title_en").asText(moduleVi), "", moduleVi, ""));
      for (JsonNode lessonData : moduleData.path("lessons")) {
        String lessonCode = lessonData.path("id").asText();
        String lessonVi = lessonData.path("title_vi").asText();
        String lessonEn = lessonData.path("title_en").asText(lessonVi);
        String type =
            switch (lessonData.path("type").asText()) {
              case "vocabulary" -> "VOCABULARY";
              case "grammar" -> "GRAMMAR";
              case "review", "assessment" -> "REVIEW";
              case "dialogue", "speaking" -> "CONVERSATION";
              default -> "NORMAL";
            };
        long lesson =
            courses.addLesson(
                module,
                new ContentRequests.Lesson(
                    lessonCode,
                    lessonCode.toLowerCase(Locale.ROOT),
                    lessonEn,
                    lessonEn,
                    type,
                    lessonData.path("estimated_minutes").asInt(15),
                    previous == null ? List.of() : List.of(previous),
                    lessonEn,
                    lessonVi,
                    lessonVi,
                    lessonVi));
        previous = lesson;
        for (JsonNode block : lessonData.path("blocks")) {
          String blockType = block.path("type").asText();
          switch (blockType) {
            case "quiz" -> {
              boolean finalAssessment =
                  lessonData.path("type").asText().equals("review")
                      || (code.equals("TV123-C00") && lessonData.path("position").asInt() == 5);
              long quiz =
                  quizzes.create(
                      new ContentRequests.Quiz(
                          "Assessment: " + lessonEn,
                          "Pass with at least 70%.",
                          finalAssessment ? "COURSE" : "LESSON",
                          course,
                          lesson,
                          BigDecimal.valueOf(70),
                          null,
                          null,
                          false,
                          "Đánh giá: " + lessonVi,
                          "Đạt từ 70% số câu tự chấm."));
              int count = 0;
              for (JsonNode q : block.path("questions")) {
                ContentRequests.Question mapped = question(q);
                if (mapped == null) {
                  activity(
                      lesson,
                      "TEXT",
                      "Câu trả lời tự luận",
                      "Open response",
                      text(q.path("prompt_vi").asText()),
                      false,
                      null);
                  continue;
                }
                long questionId = questions.create(mapped);
                quizzes.addQuestion(
                    quiz,
                    new ContentRequests.QuizQuestion(
                        questionId, BigDecimal.valueOf(q.path("points").asInt(1))));
                count++;
              }
              if (count > 0) {
                quizzes.status(quiz, "PUBLISHED");
                activity(lesson, "QUIZ", "Kiểm tra", "Quiz", json.createObjectNode(), true, quiz);
                if (finalAssessment) finalQuiz = quiz;
              }
            }
            case "illustration" -> {
              JsonNode asset = block.path("asset");
              String filename = asset.path("path").asText().replace("assets/", "");
              if (!filename.matches("[a-zA-Z0-9_-]+\\.jpg")
                  || !new ClassPathResource("static/media/tv123/package/" + filename).exists())
                throw new IllegalStateException("Missing package image " + filename);
              ObjectNode image = json.createObjectNode();
              image.put("imageUrl", "/media/tv123/package/" + filename);
              image.put("captionVi", asset.path("caption_vi").asText());
              activity(lesson, "IMAGE", "Hình minh họa", "Illustration", image, true, null);
            }
            case "dialogue" -> {
              ObjectNode dialogue = json.createObjectNode();
              ArrayNode lines = dialogue.putArray("lines");
              for (JsonNode line : block.path("lines"))
                lines
                    .addObject()
                    .put("speaker", line.path("speaker").asText())
                    .put("textVi", line.path("vi").asText())
                    .put("textEn", line.path("en").asText());
              activity(lesson, "DIALOGUE", "Hội thoại", "Dialogue", dialogue, true, null);
            }
            case "vocabulary" -> {
              ObjectNode vocabulary = json.createObjectNode();
              ArrayNode items = vocabulary.putArray("items");
              for (JsonNode item : block.path("items"))
                items
                    .addObject()
                    .put("wordVi", item.path("term_vi").asText())
                    .put("meaningEn", item.path("meaning_en").asText())
                    .put("exampleVi", item.path("example_vi").asText());
              activity(lesson, "VOCABULARY", "Từ vựng", "Vocabulary", vocabulary, true, null);
            }
            // There is no recording in the package. Do not publish a listening completion gate.
            case "listening_task", "shadowing", "teacher_note" -> {}
            default -> {
              String body = body(block);
              if (!body.isBlank())
                activity(
                    lesson, "TEXT", label(blockType), label(blockType), text(body), true, null);
            }
          }
        }
        courses.lessonStatus(lesson, "PUBLISHED");
      }
    }
    if (finalQuiz != null)
      db.update("UPDATE courses SET final_assessment_quiz_id=? WHERE id=?", finalQuiz, course);
    courses.status(course, "PUBLISHED");
    return course;
  }

  private ContentRequests.Question question(JsonNode q) {
    String type = q.path("type").asText();
    ObjectNode key = json.createObjectNode();
    ArrayNode options = json.createArrayNode();
    switch (type) {
      case "single_choice" -> {
        int index = q.path("correct_option_index").asInt(-1);
        JsonNode choices = q.path("options");
        if (index < 0 || index >= choices.size() || choices.size() < 2)
          throw new IllegalStateException("Invalid choice question");
        for (int i = 0; i < choices.size(); i++)
          options
              .addObject()
              .put("id", String.valueOf(i + 1))
              .put("textVi", choices.get(i).asText());
        key.putArray("optionIds").add(String.valueOf(index + 1));
        type = "MULTIPLE_CHOICE";
      }
      case "fill_blank" -> {
        ArrayNode answers = key.putArray("acceptedAnswers");
        for (JsonNode answer : q.path("accepted_answers")) answers.add(answer.asText());
        if (answers.isEmpty()) throw new IllegalStateException("Missing fill-blank answer");
        type = "FILL_BLANK";
      }
      case "reorder_tokens" -> {
        List<String> remaining = new ArrayList<>();
        for (JsonNode token : q.path("tokens")) remaining.add(token.asText());
        List<String> order = new ArrayList<>();
        String sentence = normalize(q.path("correct_sentence").asText());
        while (!sentence.isBlank()) {
          int found = -1;
          int longest = 0;
          for (int i = 0; i < remaining.size(); i++) {
            String token = normalize(remaining.get(i));
            if (token.length() > longest
                && (sentence.equals(token) || sentence.startsWith(token + " "))) {
              found = i;
              longest = token.length();
            }
          }
          if (found < 0) return null;
          order.add(remaining.remove(found));
          sentence = sentence.substring(longest).strip();
        }
        if (!remaining.isEmpty()) return null;
        for (int i = 0; i < q.path("tokens").size(); i++)
          options
              .addObject()
              .put("id", String.valueOf(i + 1))
              .put("textVi", q.path("tokens").get(i).asText());
        ArrayNode sequence = key.putArray("sequence");
        List<String> used = new ArrayList<>();
        for (String token : order) {
          for (int i = 0; i < q.path("tokens").size(); i++) {
            String id = String.valueOf(i + 1);
            if (!used.contains(id) && q.path("tokens").get(i).asText().equals(token)) {
              sequence.add(id);
              used.add(id);
              break;
            }
          }
        }
        type = "REORDER_SENTENCE";
      }
      default -> {
        return null;
      }
    }
    String prompt = q.path("prompt_vi").asText();
    String explanation = q.path("explanation_vi").asText("");
    return new ContentRequests.Question(
        type,
        prompt,
        explanation,
        "EASY",
        "TV123 package",
        null,
        null,
        "PUBLISHED",
        key,
        options,
        prompt,
        explanation);
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC)
        .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "")
        .toLowerCase(Locale.ROOT);
  }

  private ObjectNode text(String body) {
    return json.createObjectNode().put("body", body);
  }

  private String body(JsonNode block) {
    List<String> parts = new ArrayList<>();
    for (String field :
        List.of("text_vi", "note", "focus", "prompt_vi", "student_instruction_vi", "content"))
      if (block.path(field).isTextual()) parts.add(block.path(field).asText());
    for (String field : List.of("items", "instructions_vi", "steps", "sentences_vi", "questions")) {
      for (JsonNode item : block.path(field)) {
        if (item.isTextual()) parts.add("• " + item.asText());
        else if (item.isArray()) parts.add("• " + join(item));
        else if (item.isObject()) {
          List<String> values = new ArrayList<>();
          for (String key :
              List.of(
                  "structure_vi",
                  "explanation_vi",
                  "example_vi",
                  "example_en",
                  "letter",
                  "word",
                  "en",
                  "name_vi",
                  "mark",
                  "content")) if (item.path(key).isTextual()) values.add(item.path(key).asText());
          if (!values.isEmpty()) parts.add("• " + String.join(" — ", values));
        }
      }
    }
    return String.join("\n", parts);
  }

  private static String join(JsonNode items) {
    List<String> words = new ArrayList<>();
    for (JsonNode item : items) words.add(item.asText());
    return String.join(", ", words);
  }

  private static String label(String type) {
    return type.replace('_', ' ');
  }

  private void activity(
      long lesson,
      String type,
      String titleVi,
      String titleEn,
      JsonNode content,
      boolean required,
      Long quiz) {
    activities.create(
        lesson,
        new ContentRequests.Activity(
            type,
            titleEn,
            null,
            content,
            required,
            BigDecimal.ZERO,
            "PUBLISHED",
            quiz,
            List.of(),
            titleVi,
            null));
  }
}
