package com.sep490.backend.learning.lesson;

import static com.sep490.backend.learning.shared.ContentRules.*;
import static com.sep490.backend.learning.shared.ContentStore.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.progress.ActivityProgressService;
import com.sep490.backend.learning.quiz.QuestionScoring;
import com.sep490.backend.learning.shared.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {
  private final ContentStore db;
  private final ActivityProgressService progress;
  private final CourseContentService courses;
  private final QuestionScoring scoring;
  private static final Set<String> PRACTICE =
      Set.of(
          "MULTIPLE_CHOICE",
          "FILL_BLANK",
          "MATCHING",
          "REORDER_SENTENCE",
          "LISTEN_AND_CHOOSE",
          "LISTEN_AND_TYPE",
          "LISTENING",
          "TRANSLATION",
          "PRACTICE");

  public Map<String, Object> lesson(long id, Long user, boolean admin) {
    var l =
        admin ? db.one("SELECT * FROM lessons WHERE id=?", id) : progress.access(user, id, false);
    l.put("moduleId", l.get("unitId"));
    l.put("estimatedMinutes", l.get("estimatedDuration"));
    l.put(
        "courseTitle",
        db.one("SELECT title FROM courses WHERE id=?", l.get("courseId")).get("title"));
    l.put(
        "moduleTitle",
        l.get("unitId") == null
            ? "Lessons"
            : db.one("SELECT title_en AS title FROM course_units WHERE id=?", l.get("unitId"))
                .get("title"));
    var course = db.one("SELECT title,title_vi FROM courses WHERE id=?",l.get("courseId"));
    l.put("courseTitleVi",course.get("titleVi")); l.put("courseTitleEn",course.get("titleEn"));
    var module = l.get("unitId")==null ? Map.<String,Object>of("titleVi","Bài học","titleEn","Lessons")
        : db.one("SELECT title_vi,title_en FROM course_units WHERE id=?",l.get("unitId"));
    l.put("moduleTitleVi",module.get("titleVi")); l.put("moduleTitleEn",module.get("titleEn"));
    l.put(
        "prerequisiteIds",
        db
            .rows("SELECT prerequisite_lesson_id FROM lesson_prerequisites WHERE lesson_id=?", id)
            .stream()
            .map(r -> r.get("prerequisiteLessonId"))
            .toList());
    l.put("activities", activities(id, user, admin));
    // Preserve old navigation metadata while all pages consume this one lesson response.
    var ordered = db.rows("SELECT id FROM lessons WHERE course_id=? AND published=true ORDER BY lesson_order,id",l.get("courseId"));
    l.put("previousLessonId",null); l.put("nextLessonId",null);
    for(int i=0;i<ordered.size();i++) if(id(ordered.get(i),"id")==id) {
      if(i>0) l.put("previousLessonId",ordered.get(i-1).get("id"));
      if(i+1<ordered.size()) l.put("nextLessonId",ordered.get(i+1).get("id"));
    }
    if (!admin) l.put("progress", progress.lessonProgress(user, id));
    return l;
  }

  public List<Map<String, Object>> activities(long lesson, Long user, boolean admin) {
    if (!admin) progress.access(user, lesson, false);
    var list =
        db.rows(
            "SELECT * FROM lesson_activities WHERE lesson_id=?"
                + (admin ? "" : " AND status='PUBLISHED'")
                + " ORDER BY order_index",
            lesson);
    for (var a : list) {
      JsonNode content = db.json(a.get("contentJson"));
      if (!admin) content = safeContent(a.get("activityType").toString(), content);
      a.put("contentJson", content);
      a.put(
          "grammarTopics",
          db.rows(
              "SELECT g.* FROM grammar_topics g JOIN activity_grammar_topics ag ON"
                  + " g.id=ag.grammar_topic_id WHERE ag.activity_id=?"
                  + (admin ? "" : " AND g.status='PUBLISHED'")
                  + " ORDER BY ag.order_index",
              a.get("id")));
    }
    for (var a : list) {
      @SuppressWarnings("unchecked") var topics = (List<Map<String,Object>>) a.get("grammarTopics");
      for (var g : topics) g.put("examples",db.rows("SELECT * FROM grammar_examples WHERE grammar_topic_id=? ORDER BY order_index",g.get("id")));
    }
    return list;
  }

  private JsonNode safeContent(String type, JsonNode original) {
    ObjectNode safe = new ObjectMapper().createObjectNode();
    // Explicit allowlist prevents answer configuration leaking through nested metadata.
    for (String key :
        List.of(
            "body", "textVi", "textEn", "promptVi", "promptEn", "transcriptVi", "transcriptEn", "captionVi", "captionEn",
            "imageUrl",
            "audioUrl",
            "videoUrl",
            "caption",
            "transcript",
            "translation",
            "vocabularyIds",
            "scenarioId",
            "targetText",
            "prompt",
            "questionType")) if (original.has(key) && original.get(key).isValueNode()) safe.set(key, original.get(key));
    if (original.path("vocabularyIds").isArray()) {
      var ids = safe.putArray("vocabularyIds");
      original.path("vocabularyIds").forEach(v -> { if(v.isIntegralNumber()) ids.add(v.longValue()); });
    }
    if (original.path("items").isArray()) {
      var items = safe.putArray("items");
      for (var item : original.path("items")) {
        var out=items.addObject();
        for (String k : List.of("wordVi","meaningEn","pronunciation","wordType","exampleVi","exampleEn","audioUrl","imageUrl"))
          if(item.has(k) && item.get(k).isValueNode()) out.set(k,item.get(k));
      }
    }
    if (original.has("options"))
      safe.set(
          "options", new ObjectMapper().valueToTree(scoring.safeOptions(original.get("options"))));
    if (original.has("lines")) {
      var lines = safe.putArray("lines");
      for (var line : original.path("lines")) {
        var out = lines.addObject();
        for (String k : List.of("speaker", "text", "translation", "textVi", "textEn", "audioUrl"))
          if (line.has(k) && line.get(k).isValueNode()) out.set(k, line.get(k));
      }
    }
    return safe;
  }

  @Transactional
  public long create(long lesson, ContentRequests.Activity r) {
    var l = db.one("SELECT * FROM lessons WHERE id=?", lesson);
    courses.lock(id(l, "courseId"));
    validate(r, lesson);
    long order =
        db.count(
            "SELECT COALESCE(MAX(order_index),0)+1 FROM lesson_activities WHERE lesson_id=?",
            lesson);
    long id =
        db.insert(
            "INSERT INTO"
                + " lesson_activities(lesson_id,activity_type,title,instruction,content_json,order_index,is_required,max_score,status,quiz_id)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
            lesson,
            r.activityType(),
            r.title(),
            r.instruction(),
            db.encode(r.contentJson()),
            order,
            r.isRequired(),
            r.maxScore(),
            r.status() == null ? "DRAFT" : r.status(),
            r.quizId());
    bilingual(id,r);
    grammar(id, r);
    return id;
  }

  @Transactional
  public void edit(long id, ContentRequests.Activity r) {
    var a = db.one("SELECT * FROM lesson_activities WHERE id=?", id);
    long lesson = id(a, "lessonId");
    courses.lock(id(db.one("SELECT course_id FROM lessons WHERE id=?", lesson), "courseId"));
    validate(r, lesson);
    if (db.count("SELECT count(*) FROM activity_progress WHERE activity_id=?", id) > 0
        && (!a.get("activityType").equals(r.activityType())
            || !Objects.equals(a.get("quizId"), r.quizId())
            || !db.json(a.get("contentJson")).equals(r.contentJson())))
      throw ContentException.conflict(
          "ACTIVITY_HAS_PROGRESS",
          "Archive this activity and create a new one to change assessed content; learner history"
              + " is retained.");
    db.update(
        "UPDATE lesson_activities SET"
            + " activity_type=?,title=?,instruction=?,content_json=?,is_required=?,max_score=?,status=?,quiz_id=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.activityType(),
        r.title(),
        r.instruction(),
        db.encode(r.contentJson()),
        r.isRequired(),
        r.maxScore(),
        r.status() == null ? "DRAFT" : r.status(),
        r.quizId(),
        id);
    db.update("DELETE FROM activity_grammar_topics WHERE activity_id=?", id);
    grammar(id, r);
  }

  private void bilingual(long id, ContentRequests.Activity r) {
    db.update("UPDATE lesson_activities SET title_vi=COALESCE(?,title_vi),instruction_vi=COALESCE(?,instruction_vi) WHERE id=?",r.titleVi(),r.instructionVi(),id);
  }

  private void validate(ContentRequests.Activity r, long lesson) {
    member(r.activityType(), ACTIVITIES);
    member(r.status() == null ? "DRAFT" : r.status(), Set.of("DRAFT", "PUBLISHED", "ARCHIVED"));
    if (!r.contentJson().isObject() || db.encode(r.contentJson()).length() > 100000)
      throw ContentException.invalid("Activity content must be a JSON object under 100 KB.");
    for (String key : List.of("imageUrl", "audioUrl", "videoUrl"))
      url(r.contentJson().path(key).asText(null));
    for (var line : r.contentJson().path("lines")) url(line.path("audioUrl").asText(null));
    if (r.isRequired()
        && "PUBLISHED".equals(r.status())
        && Set.of("AI_CONVERSATION", "AI_ROLEPLAY", "PRONUNCIATION").contains(r.activityType()))
      throw ContentException.invalid(
          "Publish external integration activities as optional until verified Member 4 completion"
              + " is connected.");
    if (PRACTICE.contains(r.activityType()))
      scoring.validate(
          questionType(r.activityType(), r.contentJson()),
          r.contentJson().path("options"),
          r.contentJson().path("correctAnswer"));
    if (r.activityType().equals("QUIZ")) {
      if (r.quizId() == null) throw ContentException.invalid("Select a quiz.");
      var q = db.one("SELECT * FROM quizzes WHERE id=?", r.quizId());
      if (!Objects.equals(q.get("lessonId"), lesson))
        throw ContentException.invalid("Quiz must belong to this lesson.");
      if ("PUBLISHED".equals(r.status()) && !"PUBLISHED".equals(q.get("status")))
        throw ContentException.invalid("Publish the quiz first.");
    }
    if (r.activityType().equals("GRAMMAR")
        && (r.grammarTopicIds() == null || r.grammarTopicIds().isEmpty()))
      throw ContentException.invalid("Select at least one grammar topic.");
    if (r.grammarTopicIds() != null) {
      if (new HashSet<>(r.grammarTopicIds()).size() != r.grammarTopicIds().size())
        throw ContentException.invalid("Duplicate grammar topics.");
      for (long g : r.grammarTopicIds())
        db.one(
            "SELECT id FROM grammar_topics WHERE id=?"
                + ("PUBLISHED".equals(r.status()) ? " AND status='PUBLISHED'" : ""),
            g);
    }
  }

  private void grammar(long id, ContentRequests.Activity r) {
    if (r.grammarTopicIds() != null)
      for (int i = 0; i < r.grammarTopicIds().size(); i++)
        db.update(
            "INSERT INTO activity_grammar_topics VALUES(?,?,?)",
            id,
            r.grammarTopicIds().get(i),
            i + 1);
  }

  @Transactional
  public void delete(long id) {
    var a = db.one("SELECT * FROM lesson_activities WHERE id=? FOR UPDATE", id);
    if (db.count("SELECT count(*) FROM activity_progress WHERE activity_id=?", id) > 0) {
      db.update("UPDATE lesson_activities SET status='ARCHIVED' WHERE id=?", id);
      return;
    }
    db.update("DELETE FROM activity_grammar_topics WHERE activity_id=?", id);
    db.update("DELETE FROM lesson_activities WHERE id=?", id);
  }

  @Transactional
  public void reorder(long lesson, List<Long> ids) {
    var l = db.one("SELECT * FROM lessons WHERE id=?", lesson);
    courses.lock(id(l, "courseId"));
    exactOrder(
        ids,
        db.rows("SELECT id FROM lesson_activities WHERE lesson_id=?", lesson).stream()
            .map(a -> id(a, "id"))
            .toList());
    db.update("UPDATE lesson_activities SET order_index=-order_index WHERE lesson_id=?", lesson);
    for (int i = 0; i < ids.size(); i++)
      db.update("UPDATE lesson_activities SET order_index=? WHERE id=?", i + 1, ids.get(i));
  }

  @Transactional
  public Map<String, Object> interact(long user, long activity, JsonNode answer, boolean complete) {
    progress.lockUser(user);
    var a = db.one("SELECT * FROM lesson_activities WHERE id=? AND status='PUBLISHED'", activity);
    long lesson = id(a, "lessonId");
    progress.startLesson(user, lesson);
    var old =
        db.rows(
            "SELECT * FROM activity_progress WHERE user_id=? AND activity_id=?", user, activity);
    if (!old.isEmpty() && "COMPLETED".equals(old.getFirst().get("status"))) {
      var result = progress.evaluate(user, lesson);
      result.put("activityCompleted", true);
      return result;
    }
    if (old.isEmpty())
      db.insert(
          "INSERT INTO activity_progress(user_id,activity_id,status) VALUES(?,?,'IN_PROGRESS')",
          user,
          activity);
    boolean passed = complete;
    String type = a.get("activityType").toString();
    JsonNode content = db.json(a.get("contentJson"));
    if (complete && PRACTICE.contains(type))
      passed =
          scoring.correct(
              questionType(type, content),
              content.path("options"),
              content.path("correctAnswer"),
              answer);
    if (complete && type.equals("QUIZ"))
      passed =
          db.count(
                  "SELECT count(*) FROM quiz_attempts WHERE user_id=? AND quiz_id=? AND passed=true"
                      + " AND status='SUBMITTED'",
                  user,
                  a.get("quizId"))
              > 0;
    if (complete && Set.of("AI_CONVERSATION", "AI_ROLEPLAY", "PRONUNCIATION").contains(type))
      throw ContentException.conflict(
          "INTEGRATION_PENDING",
          "This activity needs a verified Member 4 completion callback. Demo interaction does not"
              + " award progress.");
    db.update(
        "UPDATE activity_progress SET"
            + " status=?,progress_percent=?,score=?,attempts=attempts+?,answer_json=?,last_attempt_at=CURRENT_TIMESTAMP,completed_at=?"
            + " WHERE user_id=? AND activity_id=?",
        passed ? "COMPLETED" : "IN_PROGRESS",
        passed ? 100 : 0,
        passed ? a.get("maxScore") : BigDecimal.ZERO,
        complete ? 1 : 0,
        answer == null ? null : db.encode(answer),
        passed ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
        user,
        activity);
    var result = progress.evaluate(user, lesson);
    result.put("activityCompleted", passed);
    return result;
  }

  public static String questionType(String type, JsonNode content) {
    return switch (type) {
      case "LISTEN_AND_CHOOSE", "LISTEN_AND_TYPE" -> "LISTENING";
      case "PRACTICE" -> content.path("questionType").asText("FILL_BLANK");
      default -> type;
    };
  }
}
