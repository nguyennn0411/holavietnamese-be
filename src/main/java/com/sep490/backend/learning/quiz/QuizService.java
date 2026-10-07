package com.sep490.backend.learning.quiz;

import static com.sep490.backend.learning.shared.ContentStore.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep490.backend.learning.progress.ActivityProgressService;
import com.sep490.backend.learning.shared.*;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {
  private final ContentStore db;
  private final QuestionScoring scoring;
  private final ActivityProgressService progress;
  private final Clock clock;

  public PageResponse<Map<String, Object>> list(Map<String, String> p) {
    return ContentList.query(
        db,
        "SELECT q.*,(SELECT count(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id) AS"
            + " question_count,(SELECT count(*) FROM quiz_attempts a WHERE a.quiz_id=q.id) AS"
            + " attempt_count",
        "FROM quizzes q",
        "1=1",
        "q.title,q.description",
        Map.of("quizType", "q.quiz_type", "status", "q.status"),
        Map.of("id", "q.id", "title", "q.title", "updatedAt", "q.updated_at"),
        p);
  }

  public Map<String, Object> detail(long id, boolean admin) {
    var q =
        db.one("SELECT * FROM quizzes WHERE id=?" + (admin ? "" : " AND status='PUBLISHED'"), id);
    q.put(
        "questions",
        admin
            ? db.rows(
                "SELECT qq.*,q.prompt,q.question_type,q.status,q.current_version FROM"
                    + " quiz_questions qq JOIN questions q ON q.id=qq.question_id WHERE"
                    + " qq.quiz_id=? ORDER BY qq.order_index",
                id)
            : List.of());
    q.put("questionCount", db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=?", id));
    return q;
  }

  private void validate(ContentRequests.Quiz r) {
    ContentRules.member(r.quizType(), Set.of("LESSON", "COURSE", "PLACEMENT", "PRACTICE"));
    if (r.lessonId() != null) {
      var l = db.one("SELECT course_id FROM lessons WHERE id=?", r.lessonId());
      if (r.courseId() == null || id(l, "courseId") != r.courseId())
        throw ContentException.invalid("Select the lesson's course.");
    }
    if (r.courseId() != null) db.one("SELECT id FROM courses WHERE id=?", r.courseId());
    if (r.quizType().equals("LESSON") && r.lessonId() == null)
      throw ContentException.invalid("Lesson quiz requires a lesson.");
    if (r.quizType().equals("COURSE") && r.courseId() == null)
      throw ContentException.invalid("Course quiz requires a course.");
  }

  @Transactional
  public long create(ContentRequests.Quiz r) {
    validate(r);
    long quiz = db.insert(
        "INSERT INTO"
            + " quizzes(title,description,quiz_type,course_id,lesson_id,passing_score,time_limit_minutes,max_attempts,randomize_questions,status)"
            + " VALUES(?,?,?,?,?,?,?,?,?,'DRAFT')",
        r.title(),
        r.description(),
        r.quizType(),
        r.courseId(),
        r.lessonId(),
        r.passingScore(),
        r.timeLimitMinutes(),
        r.maxAttempts(),
        r.randomizeQuestions());
    bilingual(quiz,r);
    return quiz;
  }

  @Transactional
  public void edit(long id, ContentRequests.Quiz r) {
    var q = db.one("SELECT * FROM quizzes WHERE id=? FOR UPDATE", id);
    validate(r);
    if (db.count("SELECT count(*) FROM quiz_attempts WHERE quiz_id=?", id) > 0
        && (!Objects.equals(q.get("lessonId"), r.lessonId())
            || !Objects.equals(q.get("courseId"), r.courseId())))
      throw ContentException.invalid("Cannot move a quiz with attempt history.");
    db.update(
        "UPDATE quizzes SET"
            + " title=?,description=?,quiz_type=?,course_id=?,lesson_id=?,passing_score=?,time_limit_minutes=?,max_attempts=?,randomize_questions=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.title(),
        r.description(),
        r.quizType(),
        r.courseId(),
        r.lessonId(),
        r.passingScore(),
        r.timeLimitMinutes(),
        r.maxAttempts(),
        r.randomizeQuestions(),
        id);
    bilingual(id,r);
  }

  private void bilingual(long id, ContentRequests.Quiz r) {
    db.update("UPDATE quizzes SET title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi) WHERE id=?",r.titleVi(),r.descriptionVi(),id);
  }

  @Transactional
  public void status(long id, String status) {
    ContentRules.member(status, ContentRules.STATUSES);
    db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", id);
    if (status.equals("PUBLISHED")) usable(id);
    db.update(
        "UPDATE quizzes SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        status,
        id);
  }

  private List<Map<String, Object>> usable(long quiz) {
    var rows =
        db.rows(
            "SELECT qq.*,q.status,v.id AS"
                + " version_id,v.question_type,v.correct_answer_json,v.options_snapshot_json FROM"
                + " quiz_questions qq JOIN questions q ON q.id=qq.question_id JOIN"
                + " question_versions v ON v.question_id=q.id AND"
                + " v.version_number=q.current_version WHERE qq.quiz_id=? ORDER BY qq.order_index",
            quiz);
    if (rows.isEmpty()) throw ContentException.invalid("Quiz needs at least one question.");
    for (var r : rows) {
      if (!"PUBLISHED".equals(r.get("status")))
        throw ContentException.invalid(
            "Publish every selected question before this quiz can be taken.");
      scoring.validate(
          r.get("questionType").toString(),
          db.json(r.get("optionsSnapshotJson")),
          db.json(r.get("correctAnswerJson")));
    }
    return rows;
  }

  @Transactional
  public void addQuestion(long quiz, ContentRequests.QuizQuestion r) {
    db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", quiz);
    db.one("SELECT id FROM questions WHERE id=?", r.questionId());
    if (db.count(
            "SELECT count(*) FROM quiz_questions WHERE quiz_id=? AND question_id=?",
            quiz,
            r.questionId())
        > 0)
      db.update(
          "UPDATE quiz_questions SET points=? WHERE quiz_id=? AND question_id=?",
          r.points(),
          quiz,
          r.questionId());
    else
      db.update(
          "INSERT INTO quiz_questions VALUES(?,?,?,?)",
          quiz,
          r.questionId(),
          db.count(
              "SELECT COALESCE(MAX(order_index),0)+1 FROM quiz_questions WHERE quiz_id=?", quiz),
          r.points());
    bump(quiz);
  }

  @Transactional
  public void removeQuestion(long quiz, long question) {
    db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", quiz);
    if (db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=?", quiz) <= 1
        && "PUBLISHED".equals(db.one("SELECT status FROM quizzes WHERE id=?", quiz).get("status")))
      throw ContentException.invalid("A published quiz must keep at least one question.");
    db.update("DELETE FROM quiz_questions WHERE quiz_id=? AND question_id=?", quiz, question);
    bump(quiz);
  }

  @Transactional
  public void reorder(long quiz, List<Long> ids) {
    db.one("SELECT id FROM quizzes WHERE id=? FOR UPDATE", quiz);
    ContentRules.exactOrder(
        ids,
        db.rows("SELECT question_id FROM quiz_questions WHERE quiz_id=?", quiz).stream()
            .map(r -> id(r, "questionId"))
            .toList());
    db.update("UPDATE quiz_questions SET order_index=-order_index WHERE quiz_id=?", quiz);
    for (int i = 0; i < ids.size(); i++)
      db.update(
          "UPDATE quiz_questions SET order_index=? WHERE quiz_id=? AND question_id=?",
          i + 1,
          quiz,
          ids.get(i));
    bump(quiz);
  }

  private void bump(long id) {
    db.update("UPDATE quizzes SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
  }

  private void access(long user, Map<String, Object> quiz) {
    if (quiz.get("lessonId") != null) progress.access(user, id(quiz, "lessonId"), false);
    else if (quiz.get("courseId") != null) progress.courseProgress(user, id(quiz, "courseId"));
  }

  @Transactional
  public Map<String, Object> start(long user, long quiz) {
    progress.lockUser(user);
    var q = db.one("SELECT * FROM quizzes WHERE id=? FOR UPDATE", quiz);
    if (!"PUBLISHED".equals(q.get("status")))
      throw ContentException.forbidden("QUIZ_NOT_PUBLISHED", "This quiz is not published.");
    access(user, q);
    var active =
        db.rows(
            "SELECT * FROM quiz_attempts WHERE user_id=? AND quiz_id=? AND status='IN_PROGRESS'"
                + " ORDER BY id DESC",
            user,
            quiz);
    if (!active.isEmpty()) {
      var a = active.getFirst();
      if (!expired(a)) return attempt(user, id(a, "id"), false);
      db.update("UPDATE quiz_attempts SET status='ABANDONED' WHERE id=?", a.get("id"));
    }
    if (q.get("maxAttempts") != null
        && db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND quiz_id=?", user, quiz)
            >= id(q, "maxAttempts"))
      throw ContentException.conflict(
          "MAX_ATTEMPTS_REACHED", "You have reached the attempt limit.");
    var questions = new ArrayList<>(usable(quiz));
    if (bool(q.get("randomizeQuestions"))) Collections.shuffle(questions);
    BigDecimal max =
        questions.stream()
            .map(r -> decimal(r.get("points")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var now = clock.instant();
    Timestamp expires =
        q.get("timeLimitMinutes") == null
            ? null
            : Timestamp.from(now.plusSeconds(id(q, "timeLimitMinutes") * 60));
    long attempt =
        db.insert(
            "INSERT INTO"
                + " quiz_attempts(quiz_id,user_id,quiz_version,quiz_title,quiz_title_vi,passing_score,expires_at,status,max_score,started_at)"
                + " VALUES(?,?,?,?,?,?,?,'IN_PROGRESS',?,?)",
            quiz,
            user,
            q.get("version"),
            q.get("title"),
            q.get("titleVi"),
            q.get("passingScore"),
            expires,
            max,
            Timestamp.from(now));
    for (int i = 0; i < questions.size(); i++) {
      var question = questions.get(i);
      db.insert(
          "INSERT INTO quiz_answers(attempt_id,question_id,question_version_id,order_index,points)"
              + " VALUES(?,?,?,?,?)",
          attempt,
          question.get("questionId"),
          question.get("versionId"),
          i + 1,
          question.get("points"));
    }
    return attempt(user, attempt, false);
  }

  public Map<String, Object> attempt(long user, long id, boolean admin) {
    var a = owned(user, id, admin, false);
    boolean submitted = "SUBMITTED".equals(a.get("status"));
    // Snapshot scoring configuration stays server-side until the attempt is submitted.
    if (!submitted && !admin) a.remove("passingScore");
    var answers =
        db.rows(
            "SELECT"
                + " a.*,v.version_number,v.question_type,v.prompt,v.prompt_vi,v.explanation,v.explanation_vi,v.image_url,v.audio_url,v.options_snapshot_json,v.correct_answer_json"
                + " FROM quiz_answers a JOIN question_versions v ON v.id=a.question_version_id"
                + " WHERE a.attempt_id=? ORDER BY a.order_index",
            id);
    int correct = 0, unanswered = 0;
    for (var answer : answers) {
      if (answer.get("answerJson") == null) unanswered++;
      if (bool(answer.get("isCorrect"))) correct++;
      answer.put("answer", db.json(answer.remove("answerJson")));
      answer.put("options", scoring.safeOptions(db.json(answer.remove("optionsSnapshotJson"))));
      Object key = answer.remove("correctAnswerJson");
      if (submitted || admin) answer.put("correctAnswer", db.json(key));
      else {
        answer.remove("explanation");
        answer.remove("explanationEn");
        answer.remove("explanationVi");
        answer.remove("isCorrect");
        answer.remove("score");
      }
    }
    a.put("questions", answers);
    var q = db.one("SELECT * FROM quizzes WHERE id=?", a.get("quizId"));
    a.put("lessonId", q.get("lessonId"));
    a.put("courseId", q.get("courseId"));
    a.put(
        "canRetry",
        "PUBLISHED".equals(q.get("status"))
            && (q.get("maxAttempts") == null
                || db.count(
                        "SELECT count(*) FROM quiz_attempts WHERE user_id=? AND quiz_id=?",
                        a.get("userId"),
                        a.get("quizId"))
                    < id(q, "maxAttempts")));
    if (submitted) {
      double percentage = decimal(a.get("percentage")).doubleValue();
      a.put("resultBandVi", percentage >= 85 ? "Xuất sắc" : percentage >= 70 ? "Đạt" : percentage >= 50 ? "Sắp đạt" : "Cần ôn tập");
      a.put("resultBandEn", percentage >= 85 ? "Excellent" : percentage >= 70 ? "Passed" : percentage >= 50 ? "Almost There" : "Needs Review");
      a.put("correctCount", correct);
      a.put("unansweredCount", unanswered);
      a.put("incorrectCount", answers.size() - correct - unanswered);
      a.put(
          "timeSpentSeconds",
          Duration.between(instant(a.get("startedAt")), instant(a.get("submittedAt"))).toSeconds());
    }
    a.put("serverTime", clock.instant());
    return a;
  }

  private Map<String, Object> owned(long user, long id, boolean admin, boolean lock) {
    var rows =
        db.rows(
            "SELECT * FROM quiz_attempts WHERE id=?"
                + (admin ? "" : " AND user_id=?")
                + (lock ? " FOR UPDATE" : ""),
            admin ? new Object[] {id} : new Object[] {id, user});
    if (rows.isEmpty()) throw ContentException.missing("ATTEMPT_NOT_FOUND");
    return rows.getFirst();
  }

  private boolean expired(Map<String, Object> a) {
    return a.get("expiresAt") != null && !clock.instant().isBefore(instant(a.get("expiresAt")));
  }

  private static Instant instant(Object o) {
    if (o instanceof Timestamp t) return t.toInstant();
    if (o instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
    return Instant.parse(o.toString());
  }

  private static BigDecimal decimal(Object o) {
    return new BigDecimal(o.toString());
  }

  @Transactional
  public void save(long user, long attempt, long question, JsonNode answer) {
    progress.lockUser(user);
    var a = owned(user, attempt, false, true);
    if (!"IN_PROGRESS".equals(a.get("status")))
      throw ContentException.conflict("ATTEMPT_ALREADY_SUBMITTED", "This attempt is closed.");
    if (expired(a))
      throw ContentException.conflict(
          "QUIZ_TIME_EXPIRED", "Time has expired. Submit the saved answers.");
    if (db.encode(answer).length() > 10000) throw ContentException.invalid("Answer is too large.");
    if (db.update(
            "UPDATE quiz_answers SET answer_json=?,answered_at=? WHERE attempt_id=? AND"
                + " question_id=?",
            db.encode(answer),
            Timestamp.from(clock.instant()),
            attempt,
            question)
        != 1) throw ContentException.missing("QUESTION_VERSION_NOT_FOUND");
  }

  @Transactional
  public Map<String, Object> submit(long user, long attempt) {
    progress.lockUser(user);
    var a = owned(user, attempt, false, true);
    if (!"IN_PROGRESS".equals(a.get("status")))
      throw ContentException.conflict(
          "ATTEMPT_ALREADY_SUBMITTED",
          "This attempt has already been closed. View its saved result.");
    BigDecimal score = BigDecimal.ZERO;
    for (var answer :
        db.rows(
            "SELECT a.*,v.question_type,v.correct_answer_json,v.options_snapshot_json FROM"
                + " quiz_answers a JOIN question_versions v ON v.id=a.question_version_id WHERE"
                + " a.attempt_id=?",
            attempt)) {
      boolean correct =
          scoring.correct(
              answer.get("questionType").toString(),
              db.json(answer.get("optionsSnapshotJson")),
              db.json(answer.get("correctAnswerJson")),
              db.json(answer.get("answerJson")));
      BigDecimal points = correct ? decimal(answer.get("points")) : BigDecimal.ZERO;
      score = score.add(points);
      db.update(
          "UPDATE quiz_answers SET is_correct=?,score=? WHERE id=?",
          correct,
          points,
          answer.get("id"));
    }
    BigDecimal percent =
        score
            .multiply(BigDecimal.valueOf(100))
            .divide(decimal(a.get("maxScore")), 2, RoundingMode.HALF_UP);
    boolean passed = percent.compareTo(decimal(a.get("passingScore"))) >= 0;
    db.update(
        "UPDATE quiz_attempts SET status='SUBMITTED',score=?,percentage=?,passed=?,submitted_at=?"
            + " WHERE id=?",
        score,
        percent,
        passed,
        Timestamp.from(clock.instant()),
        attempt);
    if (passed) {
      progress.event(user, id(a, "quizId"), "QUIZ_PASSED");
      if (percent.compareTo(BigDecimal.valueOf(100)) == 0)
        progress.event(user, id(a, "quizId"), "QUIZ_PERFECT_SCORE");
      for (var activity :
          db.rows(
              "SELECT a.* FROM lesson_activities a JOIN lessons l ON l.id=a.lesson_id WHERE"
                  + " a.quiz_id=? AND a.status='PUBLISHED' AND l.published=true",
              a.get("quizId"))) {
        // Authoring changes must never prevent scoring a previously started attempt.
        // Apply progress only where the learner still has access to current content.
        if (!progress.canApplyProgress(user, id(activity, "lessonId"))) continue;
        long activityId = id(activity, "id");
        if (db.count(
                "SELECT count(*) FROM activity_progress WHERE user_id=? AND activity_id=?",
                user,
                activityId)
            == 0)
          db.insert(
              "INSERT INTO activity_progress(user_id,activity_id,status) VALUES(?,?,'IN_PROGRESS')",
              user,
              activityId);
        db.update(
            "UPDATE activity_progress SET"
                + " status='COMPLETED',progress_percent=100,score=?,attempts=attempts+1,last_attempt_at=CURRENT_TIMESTAMP,completed_at=COALESCE(completed_at,CURRENT_TIMESTAMP)"
                + " WHERE user_id=? AND activity_id=?",
            score,
            user,
            activityId);
        progress.evaluate(user, id(activity, "lessonId"));
      }
    }
    return attempt(user, attempt, false);
  }

  public Map<String, Object> result(long user, long attempt) {
    var a = owned(user, attempt, false, false);
    if (!"SUBMITTED".equals(a.get("status")))
      throw ContentException.conflict(
          "ATTEMPT_NOT_SUBMITTED", "Submit your attempt to see the result.");
    return attempt(user, attempt, false);
  }

  public List<Map<String, Object>> preview(long quiz) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (var row :
        db.rows(
            "SELECT"
                + " v.question_id,v.question_type,v.prompt,v.prompt_vi,v.image_url,v.audio_url,v.options_snapshot_json"
                + " FROM quiz_questions qq JOIN questions q ON q.id=qq.question_id JOIN"
                + " question_versions v ON v.question_id=q.id AND"
                + " v.version_number=q.current_version WHERE qq.quiz_id=? ORDER BY qq.order_index",
            quiz)) {
      row.put("options", scoring.safeOptions(db.json(row.remove("optionsSnapshotJson"))));
      result.add(row);
    }
    return result;
  }

  public PageResponse<Map<String, Object>> attempts(Map<String, String> p) {
    var filter = new HashMap<>(p);
    String base = "1=1"; // Dates are validated before insertion into SQL.
    if (p.containsKey("from") && !p.get("from").isBlank())
      base += " AND a.started_at >= '" + LocalDate.parse(p.get("from")) + "'";
    if (p.containsKey("to") && !p.get("to").isBlank())
      base += " AND a.started_at < '" + LocalDate.parse(p.get("to")).plusDays(1) + "'";
    return ContentList.query(
        db,
        "SELECT a.*,u.email AS learner",
        "FROM quiz_attempts a JOIN users u ON u.id=a.user_id",
        base,
        "u.email,a.quiz_title",
        Map.of(
            "quizId",
            "a.quiz_id",
            "userId",
            "a.user_id",
            "status",
            "a.status",
            "passed",
            "a.passed"),
        Map.of("id", "a.id", "startedAt", "a.started_at", "percentage", "a.percentage"),
        filter);
  }

  public Map<String, Object> analytics(long quiz) {
    db.one("SELECT id FROM quizzes WHERE id=?", quiz);
    var result =
        db.one(
            "SELECT count(*) AS attempt_count,COALESCE(AVG(percentage),0) AS"
                + " average_score,COALESCE(AVG(CASE WHEN passed=true THEN 100.0 ELSE 0 END),0) AS"
                + " pass_rate,COALESCE(AVG(TIMESTAMPDIFF(SECOND,started_at,submitted_at)),0)"
                + " AS average_duration_seconds FROM quiz_attempts WHERE quiz_id=? AND status='SUBMITTED'",
            quiz);
    result.put(
        "frequentlyIncorrect",
        db.rows(
            "SELECT v.question_id,v.version_number,v.prompt,count(*) AS responses,SUM(CASE WHEN"
                + " a.is_correct=false THEN 1 ELSE 0 END) AS incorrect_count FROM quiz_answers a"
                + " JOIN quiz_attempts qa ON qa.id=a.attempt_id JOIN question_versions v ON"
                + " v.id=a.question_version_id WHERE qa.quiz_id=? AND qa.status='SUBMITTED' GROUP"
                + " BY v.question_id,v.version_number,v.prompt ORDER BY incorrect_count DESC LIMIT"
                + " 10",
            quiz));
    return result;
  }
}
