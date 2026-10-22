package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.learning.course.CourseWorkbookService;
import com.sep490.backend.learning.shared.ContentStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
  @Autowired ContentStore db;

  @Test
  void templateImportsAsEditableDraftWithSharedQuizQuestion() {
    byte[] sample=withSharedFinalQuestion(workbooks.template());
    assertThat(sample).isNotEmpty();
    Map<String,Object> preview=workbooks.validate(sample,"course-studio-template.xlsx");
    assertThat(preview.get("valid")).isEqualTo(true);
    long author=db.insert("INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','CONTENT_MANAGER')",
        "workbook-"+UUID.randomUUID()+"@test","workbook-"+UUID.randomUUID(),"test");
    Map<String,Object> imported=workbooks.importDraft(sample,"course-studio-template.xlsx",author);
    long course=((Number)imported.get("courseId")).longValue();
    assertThat(db.one("SELECT status,created_by,thumbnail_url FROM courses WHERE id=?",course))
        .containsEntry("status","DRAFT").containsEntry("createdBy",author).containsEntry("thumbnailUrl",null);
    assertThat(db.count("SELECT count(*) FROM lessons WHERE course_id=? AND publication_status='DRAFT'",course)).isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM lesson_activities a JOIN lessons l ON l.id=a.lesson_id WHERE l.course_id=? AND a.status='DRAFT'",course)).isEqualTo(4);
    assertThat(db.count("SELECT count(*) FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id WHERE q.course_id=?",course)).isEqualTo(2);
    assertThat(db.count("SELECT count(DISTINCT qq.question_id) FROM quiz_questions qq JOIN quizzes q ON q.id=qq.quiz_id WHERE q.course_id=?",course)).isEqualTo(1);
  }

  private byte[] withSharedFinalQuestion(byte[] original) {
    try(var book=new XSSFWorkbook(new ByteArrayInputStream(original));var out=new ByteArrayOutputStream()) {
      var quizzes=book.getSheet("QUIZZES");var source=quizzes.getRow(1);var added=quizzes.createRow(2);
      for(int col=0;col<source.getLastCellNum();col++)added.createCell(col).setCellValue(source.getCell(col).getStringCellValue());
      added.getCell(0).setCellValue("Q2");added.getCell(2).setCellValue("COURSE");added.getCell(3).setCellValue("Final check");added.getCell(11).setCellValue("true");
      var questions=book.getSheet("QUESTIONS");source=questions.getRow(1);added=questions.createRow(2);
      for(int col=0;col<source.getLastCellNum();col++)added.createCell(col).setCellValue(source.getCell(col).getStringCellValue());
      added.getCell(1).setCellValue("Q2");
      var activities=book.getSheet("ACTIVITIES");source=activities.getRow(3);added=activities.createRow(4);
      for(int col=0;col<source.getLastCellNum();col++)added.createCell(col).setCellValue(source.getCell(col).getStringCellValue());
      added.getCell(2).setCellValue("Final quiz");added.getCell(9).setCellValue("Q2");
      book.write(out);return out.toByteArray();
    } catch(Exception ex) {throw new AssertionError(ex);}
  }
}
