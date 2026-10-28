package com.sep490.backend.modules.learning.service;
import com.sep490.backend.modules.learning.store.ContentStore;

import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.common.exception.ContentException;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ownership and approval workflow for courses authored by content managers. */
@Service
@RequiredArgsConstructor
public class CourseReviewService {
  private final ContentStore db;
  private final CourseContentService courses;

  public boolean isAdmin(LearnerPrincipal actor) {
    return actor != null && "ADMIN".equals(actor.role());
  }

  public void assertCanView(long courseId, LearnerPrincipal actor) {
    Map<String,Object> course=db.one("SELECT created_by,status FROM courses WHERE id=?",courseId);
    if ("PUBLISHED".equals(course.get("status"))) return;
    if(!isAdmin(actor) && (actor==null || course.get("createdBy")==null
        || ContentStore.id(course,"createdBy")!=actor.id()))
      throw ContentException.forbidden("COURSE_NOT_OWNED","You can only view courses assigned to you.");
  }

  public void assertCanEdit(long courseId, LearnerPrincipal actor) {
    Map<String,Object> course=db.one("SELECT c.created_by,c.submitted_by,c.status,u.role AS author_role FROM courses c LEFT JOIN users u ON u.id=c.created_by WHERE c.id=?",courseId);
    if(isAdmin(actor)) {
      if(isManagerCourse(course))
        throw ContentException.forbidden("COURSE_REVIEW_ONLY","Review content manager courses without editing their content.");
      return;
    }
    if(actor==null || course.get("createdBy")==null || ContentStore.id(course,"createdBy")!=actor.id())
      throw ContentException.forbidden("COURSE_NOT_OWNED","You can only edit courses created by you.");
    String status=course.get("status").toString();
    if(!status.equals("DRAFT")&&!status.equals("REJECTED"))
      throw ContentException.conflict("COURSE_NOT_EDITABLE","Only draft or rejected courses can be edited.");
  }

  public void assertCanChangeStatus(long courseId, String status) {
    Map<String,Object> course=db.one("SELECT c.submitted_by,u.role AS author_role FROM courses c LEFT JOIN users u ON u.id=c.created_by WHERE c.id=?",courseId);
    if(isManagerCourse(course) && ("REVIEW".equals(status) || "PUBLISHED".equals(status)))
      throw ContentException.conflict("COURSE_REVIEW_REQUIRED","Content manager courses must use the submit and review workflow.");
  }

  private boolean isManagerCourse(Map<String,Object> course) {
    return course.get("submittedBy") != null || "CONTENT_MANAGER".equals(course.get("authorRole"));
  }

  public long courseForModule(long moduleId) {
    return ContentStore.id(db.one("SELECT course_id FROM course_units WHERE id=?",moduleId),"courseId");
  }

  public long courseForLesson(long lessonId) {
    return ContentStore.id(db.one("SELECT course_id FROM lessons WHERE id=?",lessonId),"courseId");
  }

  public long courseForActivity(long activityId) {
    return ContentStore.id(db.one("SELECT l.course_id FROM lesson_activities a JOIN lessons l ON l.id=a.lesson_id WHERE a.id=?",activityId),"courseId");
  }

  @Transactional
  public void submit(long courseId, LearnerPrincipal actor) {
    assertCanEdit(courseId,actor);
    if(isAdmin(actor)) throw ContentException.forbidden("CONTENT_MANAGER_REQUIRED","Only a content manager can submit a course for review.");
    var issues = readiness(courseId);
    if (!issues.isEmpty()) throw ContentException.invalid(String.join(" ", issues));
    db.update("UPDATE courses SET status='REVIEW',submitted_by=?,submitted_at=CURRENT_TIMESTAMP,"
        +" reviewed_by=NULL,reviewed_at=NULL,review_note=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        actor.id(),courseId);
  }

  @Transactional
  public void review(long courseId, String decision, String note, LearnerPrincipal actor) {
    if(!isAdmin(actor)) throw ContentException.forbidden("ADMIN_REQUIRED","Only an administrator can review courses.");
    var course=db.one("SELECT status FROM courses WHERE id=? FOR UPDATE",courseId);
    if(!"REVIEW".equals(course.get("status")))
      throw ContentException.conflict("COURSE_NOT_IN_REVIEW","Only courses waiting for review can be approved or rejected.");
    if("APPROVE".equals(decision)) {
      var issues = readiness(courseId);
      if (!issues.isEmpty()) throw ContentException.invalid(String.join(" ", issues));
      courses.status(courseId,"PUBLISHED");
    } else if("REJECT".equals(decision)) {
      if(note==null||note.isBlank()) throw ContentException.invalid("A rejection reason is required.");
      db.update("UPDATE courses SET status='REJECTED',updated_at=CURRENT_TIMESTAMP WHERE id=?",courseId);
    } else throw ContentException.invalid("Decision must be APPROVE or REJECT.");
    db.update("UPDATE courses SET reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        actor.id(),note==null?null:note.trim(),courseId);
  }

  public List<String> readiness(long courseId) {
    db.one("SELECT id FROM courses WHERE id=?", courseId);
    List<String> issues = new ArrayList<>();
    if (db.count("SELECT count(*) FROM course_units WHERE course_id=?", courseId) == 0)
      issues.add("Add at least one module.");
    if (db.count("SELECT count(*) FROM lessons WHERE course_id=?", courseId) == 0)
      issues.add("Add at least one lesson.");
    if (db.count("SELECT count(*) FROM course_units m WHERE m.course_id=? AND NOT EXISTS"
        + " (SELECT 1 FROM lessons l WHERE l.unit_id=m.id)", courseId) > 0)
      issues.add("Every module needs a lesson.");
    if (db.count("SELECT count(*) FROM lessons WHERE course_id=? AND published=false", courseId) > 0)
      issues.add("Publish every lesson before submitting.");
    if (db.count("SELECT count(*) FROM lessons l WHERE l.course_id=? AND NOT EXISTS"
        + " (SELECT 1 FROM lesson_activities a WHERE a.lesson_id=l.id AND a.status='PUBLISHED'"
        + " AND a.is_required=true AND a.activity_type<>'QUIZ')", courseId) > 0)
      issues.add("Every lesson needs a published required learning activity.");
    if (db.count("SELECT count(*) FROM lessons l WHERE l.course_id=? AND NOT EXISTS"
        + " (SELECT 1 FROM lesson_activities a JOIN quizzes q ON q.id=a.quiz_id"
        + " WHERE a.lesson_id=l.id AND a.activity_type='QUIZ' AND a.status='PUBLISHED'"
        + " AND q.status='PUBLISHED' AND q.lesson_id=l.id"
        + " AND EXISTS (SELECT 1 FROM quiz_questions qq WHERE qq.quiz_id=q.id)"
        + " AND NOT EXISTS (SELECT 1 FROM quiz_questions qq JOIN questions x"
        + " ON x.id=qq.question_id WHERE qq.quiz_id=q.id AND x.status<>'PUBLISHED'))", courseId) > 0)
      issues.add("Every lesson needs a published quiz activity.");
    if (db.count("SELECT count(*) FROM courses c LEFT JOIN quizzes q"
        + " ON q.id=c.final_assessment_quiz_id WHERE c.id=? AND"
        + " (q.id IS NULL OR q.status<>'PUBLISHED' OR q.quiz_type<>'COURSE'"
        + " OR q.course_id<>c.id OR NOT EXISTS (SELECT 1 FROM quiz_questions qq"
        + " WHERE qq.quiz_id=q.id) OR EXISTS (SELECT 1 FROM quiz_questions qq JOIN questions x"
        + " ON x.id=qq.question_id WHERE qq.quiz_id=q.id AND x.status<>'PUBLISHED')"
        + " OR NOT EXISTS (SELECT 1 FROM lesson_activities a"
        + " WHERE a.quiz_id=q.id AND a.lesson_id=q.lesson_id AND a.status='PUBLISHED'))", courseId) > 0)
      issues.add("Set a published final course quiz and attach it to a lesson.");
    return issues;
  }
}
