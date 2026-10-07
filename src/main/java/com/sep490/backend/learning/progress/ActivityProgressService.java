package com.sep490.backend.learning.progress;

import static com.sep490.backend.learning.shared.ContentStore.*;

import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.shared.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityProgressService {
  private final ContentStore db;
  private final CourseContentService courses;
  private final Clock clock;

  public void lockUser(long user) {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", user);
  }

  public boolean canApplyProgress(long user, long lesson) {
    try {
      access(user, lesson, false);
      return true;
    } catch (ContentException unavailable) {
      return false;
    }
  }

  public Map<String, Object> access(long user, long lesson, boolean lock) {
    if (lock) lockUser(user);
    var l =
        db.one(
            "SELECT l.*,c.status AS course_status FROM lessons l JOIN courses c ON c.id=l.course_id"
                + " WHERE l.id=?",
            lesson);
    if (!bool(l.get("published"))
        || !Set.of("PUBLISHED", "ARCHIVED").contains(l.get("courseStatus")))
      throw ContentException.missing("LESSON_NOT_PUBLISHED");
    var es =
        db.rows(
            "SELECT * FROM enrollments WHERE user_id=? AND course_id=? AND status NOT IN"
                + " ('CANCELLED','DROPPED')"
                + (lock ? " FOR UPDATE" : ""),
            user,
            l.get("courseId"));
    if (es.isEmpty())
      throw ContentException.forbidden("ENROLLMENT_REQUIRED", "Enroll in this course to learn.");
    if (courses.locked(user, lesson))
      throw ContentException.forbidden("LESSON_LOCKED", "Complete prerequisite lesson first.");
    l.put("enrollmentId", es.getFirst().get("id"));
    return l;
  }

  @Transactional
  public void startLesson(long user, long lesson) {
    var l = access(user, lesson, true);
    long enrollment = id(l, "enrollmentId");
    var now = Timestamp.from(clock.instant());
    if (db.count(
            "SELECT count(*) FROM lesson_progress WHERE enrollment_id=? AND lesson_id=?",
            enrollment,
            lesson)
        == 0)
      db.insert(
          "INSERT INTO lesson_progress(enrollment_id,lesson_id,status,started_at,last_accessed_at)"
              + " VALUES(?,?,'IN_PROGRESS',?,?)",
          enrollment,
          lesson,
          now,
          now);
    else
      db.update(
          "UPDATE lesson_progress SET last_accessed_at=? WHERE enrollment_id=? AND lesson_id=?",
          now,
          enrollment,
          lesson);
    db.update(
        "UPDATE enrollments SET status=CASE WHEN status='COMPLETED' THEN status ELSE 'IN_PROGRESS'"
            + " END,started_at=COALESCE(started_at,?),last_accessed_at=?,last_accessed_lesson_id=?"
            + " WHERE id=?",
        now,
        now,
        lesson,
        enrollment);
  }

  public Map<String, Object> lessonProgress(long user, long lesson) {
    var l = access(user, lesson, false);
    return summary(user, lesson, id(l, "courseId"), false, false);
  }

  @Transactional
  public Map<String, Object> evaluate(long user, long lesson) {
    var l = access(user, lesson, true);
    long course = id(l, "courseId"), enrollment = id(l, "enrollmentId");
    long total =
        db.count(
            "SELECT count(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED' AND"
                + " is_required=true",
            lesson);
    long done =
        db.count(
            "SELECT count(*) FROM lesson_activities a JOIN activity_progress p ON"
                + " p.activity_id=a.id AND p.user_id=? AND p.status='COMPLETED' WHERE a.lesson_id=?"
                + " AND a.status='PUBLISHED' AND a.is_required=true",
            user,
            lesson);
    long all = db.count("SELECT COUNT(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED'", lesson);
    long allDone = db.count("SELECT COUNT(*) FROM lesson_activities a JOIN activity_progress p ON p.activity_id=a.id WHERE a.lesson_id=? AND a.status='PUBLISHED' AND p.user_id=? AND p.status='COMPLETED'", lesson, user);
    int percent = all == 0 ? 0 : (int) (allDone * 100 / all);
    boolean complete = total > 0 && done == total;
    startLesson(user, lesson);
    var p =
        db.one(
            "SELECT * FROM lesson_progress WHERE enrollment_id=? AND lesson_id=?",
            enrollment,
            lesson);
    boolean newly = complete && !"COMPLETED".equals(p.get("status"));
    var now = Timestamp.from(clock.instant());
    db.update(
        "UPDATE lesson_progress SET progress_percent=?"
            + ",best_score=(SELECT COALESCE(MAX(percentage),0) FROM quiz_attempts qa JOIN"
            + " quizzes q ON q.id=qa.quiz_id WHERE qa.user_id=? AND q.lesson_id=? AND"
            + " qa.status='SUBMITTED') WHERE id=?",
        percent,
        user,
        lesson,
        p.get("id"));
    if (newly) {
      db.update(
          "UPDATE lesson_progress SET status='COMPLETED',completed_at=? WHERE"
              + " id=?",
          now,
          p.get("id"));
      event(user, lesson, "LESSON_COMPLETED");
    }
    long lessons =
        db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", course);
    long completed =
        db.count(
            "SELECT count(*) FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id WHERE"
                + " p.enrollment_id=? AND p.status='COMPLETED' AND l.published=true",
            enrollment);
    boolean newCourse = false;
    boolean finalPassed = db.count(
        "SELECT count(*) FROM courses c WHERE c.id=? AND (c.final_assessment_quiz_id IS NULL OR EXISTS"
            + " (SELECT 1 FROM quiz_attempts a WHERE a.quiz_id=c.final_assessment_quiz_id AND a.user_id=? AND a.status='SUBMITTED' AND a.passed=true))",
        course, user) == 1;
    if (lessons > 0 && completed == lessons && finalPassed) {
      int changed =
          db.update(
              "UPDATE enrollments SET status='COMPLETED',completed_at=COALESCE(completed_at,?)"
                  + " WHERE id=? AND status<>'COMPLETED'",
              now,
              enrollment);
      newCourse = changed > 0;
      if (newCourse) event(user, course, "COURSE_COMPLETED");
    }
    return summary(user, lesson, course, newly, newCourse);
  }

  private Map<String, Object> summary(
      long user, long lesson, long course, boolean newly, boolean newCourse) {
    var states =
        db.rows(
            "SELECT p.* FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id WHERE"
                + " e.user_id=? AND p.lesson_id=?",
            user,
            lesson);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("lessonId", lesson);
    result.put("courseId", course);
    result.put(
        "completed", !states.isEmpty() && "COMPLETED".equals(states.getFirst().get("status")));
    long allActivities = db.count("SELECT COUNT(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED'", lesson);
    long completedActivities = db.count("SELECT COUNT(*) FROM lesson_activities a JOIN activity_progress p ON p.activity_id=a.id WHERE a.lesson_id=? AND a.status='PUBLISHED' AND p.user_id=? AND p.status='COMPLETED'", lesson, user);
    result.put("totalActivities", allActivities);
    result.put("completedActivities", completedActivities);
    result.put("progressPercent", allActivities == 0 ? 0 : completedActivities * 100 / allActivities);
    result.put("score", states.isEmpty() ? null : states.getFirst().get("bestScore"));
    result.put("newlyCompleted", newly);
    result.put("newlyCourseCompleted", newCourse);
    var progress = courseProgress(user, course);
    result.put("courseProgress", progress);
    result.put("courseCompleted", "COMPLETED".equals(progress.get("status")));
    result.put(
        "activities",
        db.rows(
            "SELECT p.* FROM activity_progress p JOIN lesson_activities a ON a.id=p.activity_id"
                + " WHERE p.user_id=? AND a.lesson_id=?",
            user,
            lesson));
    var next =
        db
            .rows(
                "SELECT l.id,l.title,l.title_vi FROM lessons l WHERE l.course_id=? AND l.published=true AND"
                    + " l.id<>? AND NOT EXISTS(SELECT 1 FROM lesson_progress p JOIN enrollments e"
                    + " ON e.id=p.enrollment_id WHERE p.lesson_id=l.id AND e.user_id=? AND"
                    + " p.status='COMPLETED') ORDER BY CASE WHEN l.lesson_order > (SELECT"
                    + " lesson_order FROM lessons WHERE id=?) THEN 0 ELSE 1 END,l.lesson_order",
                course,
                lesson,
                user,
                lesson)
            .stream()
            .filter(r -> !courses.locked(user, id(r, "id")))
            .findFirst()
            .orElse(null);
    result.put("nextLesson", next);
    return result;
  }

  public Map<String, Object> courseProgress(long user, long course) {
    var e =
        db.one(
            "SELECT * FROM enrollments WHERE user_id=? AND course_id=? AND status NOT IN"
                + " ('CANCELLED','DROPPED')",
            user,
            course);
    long total =
        db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", course);
    long done =
        db.count(
            "SELECT count(*) FROM lesson_progress p JOIN lessons l ON l.id=p.lesson_id WHERE"
                + " p.enrollment_id=? AND p.status='COMPLETED' AND l.published=true",
            e.get("id"));
    e.put("totalLessons", total);
    e.put("completedLessons", done);
    e.put("progressPercentage", total == 0 ? 0 : done * 100 / total);
    return e;
  }

  public PageResponse<Map<String, Object>> myCourses(long user, Map<String, String> p) {
    var page =
        ContentList.query(
            db,
            "SELECT c.id,c.id AS"
                + " course_id,c.title,c.title_vi,c.description,c.description_vi,c.thumbnail_url,c.level,c.estimated_duration,e.status,e.last_accessed_lesson_id,(SELECT"
                + " count(*) FROM course_units m WHERE m.course_id=c.id AND m.status='PUBLISHED')"
                + " AS module_count,(SELECT count(*) FROM lessons l WHERE l.course_id=c.id AND"
                + " l.published=true) AS lesson_count",
            "FROM courses c JOIN enrollments e ON e.course_id=c.id",
            "e.user_id=" + user + " AND e.status NOT IN ('CANCELLED','DROPPED')",
            "c.title,c.description",
            Map.of(),
            Map.of("id", "e.id", "title", "c.title"),
            p);
    page.content()
        .forEach(
            c -> {
              c.put("progress", courseProgress(user, id(c, "id")));
            });
    return page;
  }

  public void event(long user, long entity, String type) {
    String key = type + ":" + user + ":" + entity;
    if (db.count("SELECT count(*) FROM learning_events WHERE event_key=?", key) == 0)
      db.insert(
          "INSERT INTO learning_events(event_key,event_type,user_id,entity_id,occurred_at)"
              + " VALUES(?,?,?,?,?)",
          key,
          type,
          user,
          entity,
          Timestamp.from(clock.instant()));
  }
}
