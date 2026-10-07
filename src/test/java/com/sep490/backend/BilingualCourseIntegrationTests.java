package com.sep490.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.demo.A1CourseSeed;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.progress.ActivityProgressService;
import com.sep490.backend.learning.quiz.*;
import com.sep490.backend.learning.shared.*;
import com.sep490.backend.service.CourseService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BilingualCourseIntegrationTests {
  @Autowired ContentStore db;
  @Autowired ObjectMapper json;
  @Autowired A1CourseSeed seed;
  @Autowired CourseService enrollments;
  @Autowired CourseContentService courses;
  @Autowired ActivityService activities;
  @Autowired ActivityProgressService progress;
  @Autowired QuizService quizzes;
  @Autowired QuestionBankService bank;
  @Autowired MockMvc mvc;
  long user,other,course,lesson,next;
  LearnerPrincipal learner;

  @BeforeEach void setup() throws Exception {
    user=db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,'test',true)",UUID.randomUUID()+"@a1.test");
    other=db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,'test',true)",UUID.randomUUID()+"@a1.test");
    learner=new LearnerPrincipal(user,"a1@test.local","",true);
    course=seed.seed(user);
    var lessons=db.rows("SELECT id FROM lessons WHERE course_id=? ORDER BY lesson_order",course);
    lesson=ContentStore.id(lessons.get(0),"id"); next=ContentStore.id(lessons.get(1),"id");
    enrollments.enroll(user,course);
  }

  @Test void seedContainsRealBilingualLessonsAndCanRunAgainWithoutOverwriting() throws Exception {
    assertThat(seed.seed(user)).isEqualTo(course);
    assertThat(db.count("SELECT count(*) FROM courses WHERE code=?",A1CourseSeed.CODE)).isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM course_units WHERE course_id=?",course)).isEqualTo(6);
    assertThat(db.count("SELECT count(*) FROM lessons WHERE course_id=?",course)).isEqualTo(19);
    assertThat(db.count("SELECT count(*) FROM quizzes WHERE course_id=?",course)).isEqualTo(25);
    for(var l:db.rows("SELECT * FROM lessons WHERE course_id=?",course)) {
      assertThat(l.get("titleVi")).isNotEqualTo(l.get("titleEn"));
      var rows=db.rows("SELECT * FROM lesson_activities WHERE lesson_id=? ORDER BY order_index",l.get("id"));
      assertThat(rows).hasSizeBetween(6,7);
      assertThat(rows.stream().map(a->a.get("activityType"))).contains("TEXT","VOCABULARY","GRAMMAR","DIALOGUE","PRACTICE","QUIZ");
      for(var a:rows) {
        assertThat(a.get("titleVi")).isNotNull();
        if(a.get("activityType").equals("VOCABULARY")) {
          var words=db.json(a.get("contentJson")).path("items");
          assertThat(words.size()).isBetween(5,12);
          for(var w:words) for(String key:List.of("wordVi","meaningEn","exampleVi","exampleEn")) assertThat(w.path(key).asText()).isNotBlank();
        }
        if(a.get("activityType").equals("QUIZ")) assertThat(db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=?",a.get("quizId"))).isBetween(5L,10L);
      }
    }
    mvc.perform(get("/api/courses/"+course).with(user(learner)))
      .andExpect(jsonPath("$.titleVi").value("Tiếng Việt cơ bản A1"))
      .andExpect(jsonPath("$.titleEn").value("Vietnamese for Beginners A1"))
      .andExpect(jsonPath("$.modules[0].titleVi").value("Làm quen với tiếng Việt"))
      .andExpect(jsonPath("$.modules[0].lessons[0].isLocked").value(false))
      .andExpect(jsonPath("$.modules[0].lessons[1].isLocked").value(true));
    mvc.perform(get("/api/courses").param("q","Tiếng Việt cơ bản A1").param("page","0"))
      .andExpect(jsonPath("$.content[0].titleVi").value("Tiếng Việt cơ bản A1"));
  }

  @Test void adminBilingualFieldsRoundTripIndependentlyAndSnapshotsRemainImmutable() throws Exception {
    long module=ContentStore.id(db.one("SELECT unit_id FROM lessons WHERE id=?",lesson),"unitId");
    courses.editModule(module,new ContentRequests.Module("English module","English description","Học phần tiếng Việt","Mô tả tiếng Việt"));
    var m=db.one("SELECT * FROM course_units WHERE id=?",module);
    assertThat(m.get("titleVi")).isEqualTo("Học phần tiếng Việt");
    assertThat(m.get("titleEn")).isEqualTo("English module");
    assertThat(m.get("descriptionVi")).isEqualTo("Mô tả tiếng Việt");
    var request=json.readValue("""
      {"titleVi":"Hoạt động Việt","titleEn":"English activity","instructionVi":"Đọc nội dung","instructionEn":"Read the content",
      "activityType":"TEXT","contentJson":{"textVi":"Xin chào","textEn":"Hello"},"isRequired":false,"maxScore":0,"status":"PUBLISHED"}
      """,ContentRequests.Activity.class);
    long activity=activities.create(lesson,request);
    assertThat(db.one("SELECT * FROM lesson_activities WHERE id=?",activity).get("titleVi")).isEqualTo("Hoạt động Việt");
    long quiz=ContentStore.id(db.one("SELECT quiz_id FROM lesson_activities WHERE lesson_id=? AND activity_type='QUIZ'",lesson),"quizId");
    long attempt=ContentStore.id(quizzes.start(user,quiz),"id");
    var q=db.one("SELECT q.* FROM questions q JOIN quiz_questions qq ON qq.question_id=q.id WHERE qq.quiz_id=? ORDER BY qq.order_index LIMIT 1",quiz);
    long qid=ContentStore.id(q,"id");
    var v=db.one("SELECT * FROM question_versions WHERE question_id=? AND version_number=1",qid);
    bank.edit(qid,new ContentRequests.Question(q.get("questionType").toString(),"New English question","New explanation","EASY",null,null,null,"PUBLISHED",
      db.json(v.get("correctAnswerJson")),db.json(v.get("optionsSnapshotJson")),"Câu hỏi mới","Giải thích mới"));
    String snapshot=db.encode(quizzes.attempt(user,attempt,false));
    assertThat(snapshot).contains(q.get("promptVi").toString()).doesNotContain("Câu hỏi mới","New English question","correctAnswer","explanationVi","explanationEn","isCorrect");
  }

  @Test void failedSixtyPercentRetainsActivitiesThenRetryPassUnlocksNext() throws Exception {
    completeNonQuiz(lesson);
    long quiz=ContentStore.id(db.one("SELECT quiz_id FROM lesson_activities WHERE lesson_id=? AND activity_type='QUIZ'",lesson),"quizId");
    long attempt=ContentStore.id(quizzes.start(user,quiz),"id");
    assertThat(ContentStore.id(quizzes.start(user,quiz),"id")).isEqualTo(attempt);
    answerCorrect(attempt,3);
    var failed=quizzes.submit(user,attempt);
    assertThat(failed.get("passed")).isEqualTo(false);
    assertThat(failed.get("percentage").toString()).isEqualTo("60.00");
    assertThat(progress.lessonProgress(user,lesson).get("completed")).isEqualTo(false);
    assertThat(courses.locked(user,next)).isTrue();
    assertThat(db.count("SELECT count(*) FROM activity_progress p JOIN lesson_activities a ON a.id=p.activity_id WHERE p.user_id=? AND a.lesson_id=? AND p.status='COMPLETED'",user,lesson)).isEqualTo(5);
    mvc.perform(post("/api/lessons/"+lesson+"/complete").with(user(learner)).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LESSON_INCOMPLETE"));
    long retry=ContentStore.id(quizzes.start(user,quiz),"id");
    assertThat(retry).isNotEqualTo(attempt);
    answerCorrect(retry,5);
    assertThat(quizzes.submit(user,retry).get("passed")).isEqualTo(true);
    assertThat(progress.lessonProgress(user,lesson).get("completed")).isEqualTo(true);
    assertThat(courses.locked(user,next)).isFalse();
    assertThat(progress.courseProgress(user,course).get("completedLessons")).isEqualTo(1L);
    db.update("UPDATE enrollments SET status='CANCELLED' WHERE user_id=? AND course_id=?",user,course);
    enrollments.enroll(user,course);
    assertThat(progress.lessonProgress(user,lesson).get("completed")).isEqualTo(true);
    assertThat(db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=?",user)).isEqualTo(2);
  }

  @Test void lockedUrlsForeignAttemptsAdminApisDraftEnrollmentAndAnswerLeaksAreBlocked() throws Exception {
    mvc.perform(get("/api/lessons/"+next).with(user(learner))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("LESSON_LOCKED"));
    mvc.perform(get("/api/admin/courses").with(user(learner))).andExpect(status().isForbidden());
    courses.status(course,"DRAFT");
    var otherPrincipal=new LearnerPrincipal(other,"other@a1.test","",true);
    mvc.perform(post("/api/courses/"+course+"/enroll").with(user(otherPrincipal)).with(csrf())).andExpect(status().isNotFound());
    courses.status(course,"PUBLISHED");
    long quiz=ContentStore.id(db.one("SELECT quiz_id FROM lesson_activities WHERE lesson_id=? AND activity_type='QUIZ'",lesson),"quizId");
    long attempt=ContentStore.id(quizzes.start(user,quiz),"id");
    mvc.perform(post("/api/quiz-attempts/"+attempt+"/submit").with(user(otherPrincipal)).with(csrf())).andExpect(status().isNotFound());
    mvc.perform(get("/api/quiz-attempts/"+attempt+"/result").with(user(learner))).andExpect(status().isConflict());
    String body=mvc.perform(get("/api/quiz-attempts/"+attempt).with(user(learner))).andReturn().getResponse().getContentAsString();
    assertThat(body).contains("promptVi","promptEn").doesNotContain("correctAnswer","acceptedAnswers","isCorrect","explanationVi","explanationEn");
    String content=db.encode(activities.activities(lesson,user,false));
    assertThat(content).contains("textVi","textEn","wordVi","exampleEn").doesNotContain("correctAnswer","acceptedAnswers");
    // Even a malicious author cannot hide an answer inside an allowlisted display field.
    db.update("UPDATE lesson_activities SET content_json=? WHERE lesson_id=? AND activity_type='TEXT'", "{\"textVi\":{\"correctAnswer\":\"secret\"},\"textEn\":\"Hello\",\"metadata\":{\"isCorrect\":true}}",lesson);
    assertThat(db.encode(activities.activities(lesson,user,false))).doesNotContain("secret","metadata","correctAnswer");
    quizzes.submit(user,attempt);
    mvc.perform(post("/api/quiz-attempts/"+attempt+"/submit").with(user(learner)).with(csrf())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_SUBMITTED"));
  }

  @Test void wholeA1CourseCompletesOnlyAfterAllLessonQuizzesAndModuleCheckpoints() {
    int done=0;
    for(var l:db.rows("SELECT id FROM lessons WHERE course_id=? ORDER BY lesson_order",course)) {
      long id=ContentStore.id(l,"id");
      assertThat(courses.locked(user,id)).isFalse();
      completeNonQuiz(id);
      var quizActivities=db.rows("SELECT quiz_id FROM lesson_activities WHERE lesson_id=? AND activity_type='QUIZ' ORDER BY order_index",id);
      for(int i=0;i<quizActivities.size();i++) {
        assertThat(progress.lessonProgress(user,id).get("completed")).isEqualTo(false);
        long attempt=ContentStore.id(quizzes.start(user,ContentStore.id(quizActivities.get(i),"quizId")),"id");
        answerCorrect(attempt,100);
        assertThat(quizzes.submit(user,attempt).get("passed")).isEqualTo(true);
      }
      assertThat(progress.lessonProgress(user,id).get("completed")).isEqualTo(true);
      assertThat(progress.courseProgress(user,course).get("completedLessons")).isEqualTo((long)++done);
    }
    var result=progress.courseProgress(user,course);
    assertThat(result.get("progressPercentage")).isEqualTo(100L);
    assertThat(result.get("status")).isEqualTo("COMPLETED");
    assertThat(db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND passed=true",user)).isEqualTo(25);
    assertThat(db.count("SELECT count(*) FROM enrollments WHERE user_id=? AND course_id=?",user,course)).isEqualTo(1);
  }

  private void completeNonQuiz(long lesson) {
    for(var a:db.rows("SELECT * FROM lesson_activities WHERE lesson_id=? AND activity_type<>'QUIZ' ORDER BY order_index",lesson)) {
      JsonNode answer=json.createObjectNode();
      if(a.get("activityType").equals("PRACTICE")) answer=answer(db.json(a.get("contentJson")).path("correctAnswer"));
      assertThat(activities.interact(user,ContentStore.id(a,"id"),answer,true).get("activityCompleted")).isEqualTo(true);
    }
  }

  private void answerCorrect(long attempt,int count) {
    int i=0;
    for(var q:db.rows("SELECT a.question_id,v.correct_answer_json FROM quiz_answers a JOIN question_versions v ON v.id=a.question_version_id WHERE a.attempt_id=? ORDER BY a.order_index",attempt)) {
      if(i++>=count) break;
      quizzes.save(user,attempt,ContentStore.id(q,"questionId"),answer(db.json(q.get("correctAnswerJson"))));
    }
  }
  private JsonNode answer(JsonNode key) {
    if(key.has("acceptedAnswers")) return json.createObjectNode().put("text",key.path("acceptedAnswers").get(0).asText());
    return key;
  }
}
