package com.sep490.backend.learning.course;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.shared.*;
import java.util.Map;
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
    Map<String,Object> course=db.one("SELECT created_by FROM courses WHERE id=?",courseId);
    if(!isAdmin(actor) && (actor==null || course.get("createdBy")==null
        || ContentStore.id(course,"createdBy")!=actor.id()))
      throw ContentException.forbidden("COURSE_NOT_OWNED","You can only view courses assigned to you.");
  }

  public void assertCanEdit(long courseId, LearnerPrincipal actor) {
    Map<String,Object> course=db.one("SELECT created_by,status FROM courses WHERE id=?",courseId);
    if(isAdmin(actor)) return;
    if(actor==null || course.get("createdBy")==null || ContentStore.id(course,"createdBy")!=actor.id())
      throw ContentException.forbidden("COURSE_NOT_OWNED","You can only edit courses created by you.");
    String status=course.get("status").toString();
    if(!status.equals("DRAFT")&&!status.equals("REJECTED"))
      throw ContentException.conflict("COURSE_NOT_EDITABLE","Only draft or rejected courses can be edited.");
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
    if(db.count("SELECT count(*) FROM course_units WHERE course_id=?",courseId)==0
        ||db.count("SELECT count(*) FROM lessons WHERE course_id=?",courseId)==0)
      throw ContentException.invalid("Add at least one module and one lesson before submitting the course.");
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
      courses.status(courseId,"PUBLISHED");
    } else if("REJECT".equals(decision)) {
      if(note==null||note.isBlank()) throw ContentException.invalid("A rejection reason is required.");
      db.update("UPDATE courses SET status='REJECTED',updated_at=CURRENT_TIMESTAMP WHERE id=?",courseId);
    } else throw ContentException.invalid("Decision must be APPROVE or REJECT.");
    db.update("UPDATE courses SET reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        actor.id(),note==null?null:note.trim(),courseId);
  }
}
