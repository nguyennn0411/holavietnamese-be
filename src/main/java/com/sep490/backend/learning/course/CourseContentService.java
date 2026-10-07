package com.sep490.backend.learning.course;

import static com.sep490.backend.learning.shared.ContentRules.*;
import static com.sep490.backend.learning.shared.ContentStore.*;

import com.sep490.backend.learning.shared.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseContentService {
  private final ContentStore db;

  public PageResponse<Map<String, Object>> list(Map<String, String> p, boolean admin) {
    return ContentList.query(
        db,
        "SELECT c.*, c.estimated_duration AS estimated_minutes, (SELECT count(*) FROM course_units"
            + " m WHERE m.course_id=c.id"
            + (admin ? "" : " AND m.status='PUBLISHED'")
            + ") AS module_count, (SELECT count(*) FROM lessons l WHERE l.course_id=c.id"
            + (admin ? "" : " AND l.published=true")
            + ") AS lesson_count",
        "FROM courses c",
        admin ? "1=1" : "c.status='PUBLISHED'",
        "c.title,c.title_vi,c.description,c.description_vi",
        Map.of("level", "c.level", "status", "c.status"),
        Map.of("id", "c.id", "title", "c.title", "updatedAt", "c.updated_at"),
        p);
  }

  public Map<String, Object> detail(long courseId, Long user, boolean admin) {
    var c =
        db.one(
            "SELECT *, estimated_duration AS estimated_minutes FROM courses WHERE id=?", courseId);
    if (!admin && !"PUBLISHED".equals(c.get("status"))) {
      if (!"ARCHIVED".equals(c.get("status"))
          || user == null
          || db.count(
                  "SELECT count(*) FROM enrollments WHERE course_id=? AND user_id=? AND status NOT"
                      + " IN ('CANCELLED','DROPPED')",
                  courseId,
                  user)
              == 0) throw ContentException.missing("COURSE_NOT_PUBLISHED");
    }
    var modules =
        db.rows(
            "SELECT id,course_id,title_en AS title,title_vi,description_en AS description,description_vi,sort_order AS"
                + " order_index,status,updated_at FROM course_units WHERE course_id=?"
                + (admin ? "" : " AND status='PUBLISHED'")
                + " ORDER BY sort_order,id",
            courseId);
    for (var m : modules) m.put("lessons", lessonRows(courseId, id(m, "id"), user, admin));
    // Imported/legacy ungrouped lessons remain visible without duplicating them into a new table.
    var ungrouped = lessonRows(courseId, null, user, admin);
    if (!ungrouped.isEmpty()) {
      var m = new LinkedHashMap<String, Object>();
      m.put("id", 0);
      m.put("title", "Lessons");
      m.put("titleEn", "Lessons");
      m.put("titleVi", "Bài học");
      m.put("lessons", ungrouped);
      modules.add(m);
    }
    c.put("modules", modules);
    c.put(
        "totalLessons",
        db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", courseId));
    c.put("moduleCount", modules.size());
    if (user != null) {
      var es =
          db.rows(
              "SELECT * FROM enrollments WHERE user_id=? AND course_id=? AND status NOT IN"
                  + " ('CANCELLED','DROPPED')",
              user,
              courseId);
      c.put("enrollment", es.isEmpty() ? null : es.getFirst());
    }
    return c;
  }

  private List<Map<String, Object>> lessonRows(long c, Long module, Long user, boolean admin) {
    List<Object> args = new ArrayList<>();
    args.add(c);
    if (module != null) args.add(module);
    var rows =
        db.rows(
            "SELECT id,course_id,unit_id AS"
                + " module_id,code,slug,title,title_vi,description,description_vi,lesson_type,estimated_duration AS"
                + " estimated_minutes,unit_sort_order AS order_index,publication_status AS"
                + " status,published FROM lessons WHERE course_id=? AND unit_id "
                + (module == null ? "IS NULL" : "=?")
                + (admin ? "" : " AND published=true")
                + " ORDER BY unit_sort_order,lesson_order,id",
            args.toArray());
    for (var l : rows) {
      long lid = id(l, "id");
      l.put("isLocked", !admin && locked(user, lid));
      var states =
          user == null
              ? List.<Map<String, Object>>of()
              : db.rows(
                  "SELECT p.status,p.progress_percent FROM lesson_progress p JOIN enrollments e ON"
                      + " e.id=p.enrollment_id WHERE e.user_id=? AND p.lesson_id=?",
                  user,
                  lid);
      l.put("learningStatus", states.isEmpty() ? "NOT_STARTED" : states.getFirst().get("status"));
    }
    return rows;
  }

  public boolean locked(Long user, long lesson) {
    return db.count(
                "SELECT count(*) FROM lesson_prerequisites r WHERE r.lesson_id=? AND NOT EXISTS"
                    + " (SELECT 1 FROM lesson_progress p JOIN enrollments e ON e.id=p.enrollment_id"
                    + " WHERE p.lesson_id=r.prerequisite_lesson_id AND e.user_id=? AND"
                    + " p.status='COMPLETED')",
                lesson,
                user)
            == 0
        ? false
        : true;
  }

  @Transactional
  public long create(ContentRequests.Course r, long author) {
    validate(r);
    long course = db.insert(
        "INSERT INTO"
            + " courses(code,slug,title,description,level,thumbnail_url,estimated_duration,learning_outcomes,is_free,status,created_by)"
            + " VALUES(?,?,?,?,?,?,?,?,?,'DRAFT',?)",
        r.code(),
        r.slug(),
        r.title(),
        r.description(),
        r.level(),
        r.thumbnailUrl(),
        r.estimatedMinutes(),
        r.learningOutcomes(),
        r.isFree(),
        author);
    bilingualCourse(course, r);
    return course;
  }

  @Transactional
  public void edit(long id, ContentRequests.Course r) {
    lock(id);
    validate(r);
    db.update(
        "UPDATE courses SET"
            + " code=?,slug=?,title=?,description=?,level=?,thumbnail_url=?,estimated_duration=?,learning_outcomes=?,is_free=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.code(),
        r.slug(),
        r.title(),
        r.description(),
        r.level(),
        r.thumbnailUrl(),
        r.estimatedMinutes(),
        r.learningOutcomes(),
        r.isFree(),
        id);
    bilingualCourse(id, r);
  }

  private void bilingualCourse(long id, ContentRequests.Course r) {
    db.update("UPDATE courses SET title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi) WHERE id=?", r.titleVi(),r.descriptionVi(),id);
  }

  private void validate(ContentRequests.Course r) {
    member(r.level(), LEVELS);
    url(r.thumbnailUrl());
  }

  public void lock(long id) {
    db.one("SELECT id FROM courses WHERE id=? FOR UPDATE", id);
  }

  @Transactional
  public void status(long id, String status) {
    lock(id);
    member(status, STATUSES);
    if (status.equals("PUBLISHED")
        && db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=true", id) == 0)
      throw ContentException.invalid("Publish at least one lesson before the course.");
    db.update("UPDATE courses SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
  }

  @Transactional
  public long addModule(long course, ContentRequests.Module r) {
    lock(course);
    long order =
        db.count(
            "SELECT COALESCE(MAX(sort_order),0)+1 FROM course_units WHERE course_id=?", course);
    return db.insert(
        "INSERT INTO"
            + " course_units(course_id,code,title_en,title_vi,description_en,description_vi,sort_order,status)"
            + " VALUES(?,?,?,?,?,?,?,'PUBLISHED')",
        course,
        "MOD-" + UUID.randomUUID(),
        r.title(),
        Objects.toString(r.titleVi(), ""),
        Objects.toString(r.description(), ""),
        Objects.toString(r.descriptionVi(), ""),
        order);
  }

  @Transactional
  public void editModule(long module, ContentRequests.Module r) {
    var m = db.one("SELECT * FROM course_units WHERE id=?", module);
    lock(id(m, "courseId"));
    db.update(
        "UPDATE course_units SET"
            + " title_en=?,title_vi=COALESCE(?,title_vi),description_en=?,description_vi=COALESCE(?,description_vi),updated_at=CURRENT_TIMESTAMP WHERE id=?",
        r.title(),
        r.titleVi(),
        Objects.toString(r.description(), ""),
        r.descriptionVi(),
        module);
  }

  @Transactional
  public void deleteModule(long module) {
    var m = db.one("SELECT * FROM course_units WHERE id=?", module);
    lock(id(m, "courseId"));
    if (db.count("SELECT count(*) FROM lessons WHERE unit_id=?", module) > 0)
      throw ContentException.invalid(
          "Only empty modules can be deleted. Archive lessons to retain history.");
    db.update("DELETE FROM course_units WHERE id=?", module);
  }

  @Transactional
  public void reorderModules(long course, List<Long> ids) {
    lock(course);
    exactOrder(
        ids,
        db.rows("SELECT id FROM course_units WHERE course_id=?", course).stream()
            .map(r -> id(r, "id"))
            .toList());
    for (int i = 0; i < ids.size(); i++)
      db.update(
          "UPDATE course_units SET sort_order=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
          i + 1,
          ids.get(i));
  }

  public PageResponse<Map<String, Object>> lessons(Map<String, String> p) {
    return ContentList.query(
        db,
        "SELECT l.*,l.unit_id AS module_id,l.estimated_duration AS estimated_minutes,c.title AS"
            + " course_title,m.title_en AS module_title,(SELECT count(*) FROM lesson_activities a"
            + " WHERE a.lesson_id=l.id) AS activity_count",
        "FROM lessons l JOIN courses c ON c.id=l.course_id LEFT JOIN course_units m ON"
            + " m.id=l.unit_id",
        "1=1",
        "l.title,l.description",
        Map.of(
            "courseId",
            "l.course_id",
            "moduleId",
            "l.unit_id",
            "lessonType",
            "l.lesson_type",
            "status",
            "l.publication_status"),
        Map.of("id", "l.id", "title", "l.title", "updatedAt", "l.updated_at"),
        p);
  }

  @Transactional
  public long addLesson(long module, ContentRequests.Lesson r) {
    var m = db.one("SELECT * FROM course_units WHERE id=?", module);
    long course = id(m, "courseId");
    lock(course);
    member(r.lessonType(), LESSON_TYPES);
    long order =
        db.count("SELECT COALESCE(MAX(lesson_order),0)+1 FROM lessons WHERE course_id=?", course);
    long unitOrder =
        db.count("SELECT COALESCE(MAX(unit_sort_order),0)+1 FROM lessons WHERE unit_id=?", module);
    long lesson =
        db.insert(
            "INSERT INTO"
                + " lessons(course_id,unit_id,unit_sort_order,code,slug,title,description,lesson_type,estimated_duration,lesson_order,content,published,publication_status)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,'',false,'DRAFT')",
            course,
            module,
            unitOrder,
            r.code(),
            r.slug(),
            r.title(),
            r.description(),
            r.lessonType(),
            r.estimatedMinutes(),
            order);
    db.update("UPDATE lessons SET learning_objective=?,title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi),learning_objective_vi=COALESCE(?,learning_objective_vi) WHERE id=?",
        r.learningObjective(),r.titleVi(),r.descriptionVi(),r.learningObjectiveVi(),lesson);
    prerequisites(lesson, course, r.prerequisiteIds());
    return lesson;
  }

  @Transactional
  public void editLesson(long lesson, ContentRequests.Lesson r) {
    var l = db.one("SELECT * FROM lessons WHERE id=?", lesson);
    long course = id(l, "courseId");
    lock(course);
    member(r.lessonType(), LESSON_TYPES);
    db.update(
        "UPDATE lessons SET"
            + " code=?,slug=?,title=?,description=?,lesson_type=?,estimated_duration=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        r.code(),
        r.slug(),
        r.title(),
        r.description(),
        r.lessonType(),
        r.estimatedMinutes(),
        lesson);
    db.update("UPDATE lessons SET learning_objective=?,title_vi=COALESCE(?,title_vi),description_vi=COALESCE(?,description_vi),learning_objective_vi=COALESCE(?,learning_objective_vi) WHERE id=?",
        r.learningObjective(),r.titleVi(),r.descriptionVi(),r.learningObjectiveVi(),lesson);
    prerequisites(lesson, course, r.prerequisiteIds());
  }

  private void prerequisites(long lesson, long course, List<Long> required) {
    var ids = required == null ? List.<Long>of() : required;
    if (new HashSet<>(ids).size() != ids.size() || ids.contains(lesson))
      throw ContentException.invalid("Invalid or duplicate prerequisite.");
    for (Long prerequisite : ids) {
      if (prerequisite == null
          || db.count(
                  "SELECT count(*) FROM lessons WHERE id=? AND course_id=?", prerequisite, course)
              != 1) throw ContentException.invalid("Prerequisites must belong to this course.");
      Set<Long> seen = new HashSet<>();
      Deque<Long> queue = new ArrayDeque<>();
      queue.add(prerequisite);
      while (!queue.isEmpty()) {
        long next = queue.remove();
        if (next == lesson) throw ContentException.invalid("Circular lesson prerequisite.");
        if (seen.add(next))
          for (var row :
              db.rows(
                  "SELECT prerequisite_lesson_id FROM lesson_prerequisites WHERE lesson_id=?",
                  next)) queue.add(id(row, "prerequisiteLessonId"));
      }
    }
    db.update("DELETE FROM lesson_prerequisites WHERE lesson_id=?", lesson);
    for (long p : ids) db.update("INSERT INTO lesson_prerequisites VALUES(?,?)", lesson, p);
  }

  @Transactional
  public void lessonStatus(long lesson, String status) {
    var l = db.one("SELECT * FROM lessons WHERE id=?", lesson);
    lock(id(l, "courseId"));
    member(status, Set.of("DRAFT", "PUBLISHED", "ARCHIVED"));
    if (status.equals("PUBLISHED")) {
      if (db.count(
              "SELECT count(*) FROM lesson_activities WHERE lesson_id=? AND status='PUBLISHED' AND"
                  + " is_required=true",
              lesson)
          == 0) throw ContentException.invalid("Publish at least one required activity first.");
      if (db.count(
              "SELECT count(*) FROM lesson_activities a LEFT JOIN quizzes q ON q.id=a.quiz_id WHERE"
                  + " a.lesson_id=? AND a.status='PUBLISHED' AND a.activity_type='QUIZ' AND (q.id"
                  + " IS NULL OR q.status<>'PUBLISHED')",
              lesson)
          > 0) throw ContentException.invalid("Publish attached quizzes first.");
    }
    db.update(
        "UPDATE lessons SET publication_status=?,published=?,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        status,
        status.equals("PUBLISHED"),
        lesson);
  }

  @Transactional
  public void reorderLessons(long module, List<Long> ids) {
    var m = db.one("SELECT * FROM course_units WHERE id=?", module);
    long course = id(m, "courseId");
    lock(course);
    exactOrder(
        ids,
        db.rows("SELECT id FROM lessons WHERE unit_id=?", module).stream()
            .map(r -> id(r, "id"))
            .toList());
    for (int i = 0; i < ids.size(); i++)
      db.update("UPDATE lessons SET unit_sort_order=? WHERE id=?", i + 1, ids.get(i));
    // Retain the legacy course-global order consumed by progress/import APIs.
    var all =
        db.rows(
            "SELECT l.id FROM lessons l LEFT JOIN course_units m ON l.unit_id=m.id WHERE"
                + " l.course_id=? ORDER BY m.sort_order,l.unit_sort_order,l.id",
            course);
    db.update("UPDATE lessons SET lesson_order=-lesson_order-1000000 WHERE course_id=?", course);
    for (int i = 0; i < all.size(); i++)
      db.update("UPDATE lessons SET lesson_order=? WHERE id=?", i + 1, id(all.get(i), "id"));
  }
}
