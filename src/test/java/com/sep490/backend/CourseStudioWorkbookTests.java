package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.admin.importing.service.CourseWorkbookService;
import com.sep490.backend.modules.learning.service.CourseReviewService;
import com.sep490.backend.modules.learning.store.ContentStore;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CourseStudioWorkbookTests {
  @Autowired CourseWorkbookService workbooks;
  @Autowired CourseReviewService reviews;
  @Autowired ContentStore db;

  @Test
  void completeTemplateImportsAsReviewReadyDraft() throws Exception {
    byte[] sample=workbooks.template();
    assertThat(sample).isNotEmpty();
    try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(sample))) {
      assertThat(workbook.getSheet("MODULES").getLastRowNum()).isEqualTo(2);
      assertThat(workbook.getSheet("LESSONS").getLastRowNum()).isEqualTo(2);
      assertThat(workbook.getSheet("QUIZZES").getLastRowNum()).isEqualTo(3);
      assertThat(workbook.getSheet("QUESTIONS").getLastRowNum()).isEqualTo(4);
      assertThat(workbook.getSheet("ACTIVITIES").getLastRowNum()).isEqualTo(6);
    }
    Map<String,Object> preview=workbooks.validate(sample,"course-studio-template.xlsx");
    assertThat(preview.get("valid")).isEqualTo(true);
    long author=db.insert("INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','CONTENT_MANAGER')",
        "workbook-"+UUID.randomUUID()+"@test","workbook-"+UUID.randomUUID(),"test");
    Map<String,Object> imported=workbooks.importDraft(sample,"course-studio-template.xlsx",author);
    long course=((Number)imported.get("courseId")).longValue();
    assertThat(db.one("SELECT status,created_by,thumbnail_url FROM courses WHERE id=?",course))
        .containsEntry("status","DRAFT").containsEntry("createdBy",author)
        .containsEntry("thumbnailUrl","/media/tv123/images/scans/cover.jpg");
    assertThat(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND publication_status='PUBLISHED'",course)).isEqualTo(2);
    assertThat(db.count("SELECT count(*) FROM lesson_activities a JOIN lessons l ON l.id=a.lesson_id WHERE l.course_id=? AND a.status='PUBLISHED'",course)).isEqualTo(6);
    assertThat(db.count("SELECT count(*) FROM quizzes WHERE course_id=? AND status='PUBLISHED'",course)).isEqualTo(3);
    assertThat(db.count("SELECT count(*) FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id WHERE q.course_id=?",course)).isEqualTo(4);
    assertThat(db.count("SELECT count(DISTINCT qq.question_id) FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id WHERE q.course_id=?",course)).isEqualTo(2);
    assertThat(reviews.readiness(course)).isEmpty();
  }
}
