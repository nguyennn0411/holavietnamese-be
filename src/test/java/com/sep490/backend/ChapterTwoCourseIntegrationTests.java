package com.sep490.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.*;
import com.sep490.backend.modules.learning.service.CourseContentService;
import com.sep490.backend.modules.learning.seed.ChapterTwoCourseSeed;
import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.service.ActivityProgressService;
import com.sep490.backend.modules.learning.service.QuizService;
import com.sep490.backend.modules.learning.store.ContentStore;
import com.sep490.backend.modules.learning.service.CourseService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChapterTwoCourseIntegrationTests {
  @Autowired ContentStore db;
  @Autowired ObjectMapper json;
  @Autowired ChapterTwoCourseSeed seed;
  @Autowired CourseService enrollment;
  @Autowired CourseContentService courses;
  @Autowired ActivityService activities;
  @Autowired ActivityProgressService progress;
  @Autowired QuizService quizzes;
  @Autowired MockMvc mvc;

  @Test void browserAudioCanLoadWithoutABearerHeader() throws Exception {
    var response = mvc.perform(get("/media/pronunciation/chapter02-1.wav")).andExpect(status().isOk()).andReturn().getResponse();
    assertThat(response.getContentAsByteArray().length).isGreaterThan(1000);
    assertThat(new String(response.getContentAsByteArray(),0,4,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
    mvc.perform(get("/api/admin/courses")).andExpect(status().isUnauthorized());
  }

  @Test void curriculumIsCompleteIdempotentAndSupportsFreeLessonOrder() throws Exception {
    long user = db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,'test',true)", UUID.randomUUID()+"@chaptertwo.test");
    long course = seed.seed(user);
    assertThat(seed.seed(user)).isEqualTo(course);
    assertThat(db.count("SELECT count(*) FROM courses WHERE code=?", ChapterTwoCourseSeed.CODE)).isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM course_units WHERE course_id=?", course)).isEqualTo(6);
    assertThat(db.count("SELECT count(*) FROM quizzes WHERE course_id=?", course)).isEqualTo(25);
    assertThat(db.count("SELECT count(*) FROM lesson_prerequisites p JOIN lessons l ON p.lesson_id=l.id WHERE l.course_id=?",course)).isEqualTo(0);
    var c=db.one("SELECT * FROM courses WHERE id=?",course);
    assertThat(c.get("level")).isEqualTo("A1");
    long finalQuiz=ContentStore.id(c,"finalAssessmentQuizId");
    assertThat(db.count("SELECT count(*) FROM quiz_questions WHERE quiz_id=?",finalQuiz)).isEqualTo(50);
    assertThat(db.count("SELECT count(*) FROM grammar_topics WHERE code LIKE ?", "VI-GREET-CH02-G%")).isEqualTo(16);
    var lessonRows=db.rows("SELECT * FROM lessons WHERE course_id=? ORDER BY lesson_order",course);
    assertThat(lessonRows).hasSize(18);
    enrollment.enroll(user,course);
    Set<Integer> ends=Set.of(3,6,9,12,15,18);
    int completed=0, failed=0;
    for(var l:lessonRows){
      long lid=ContentStore.id(l,"id");
      int n=completed+1;
      assertThat(courses.locked(user,lid)).isFalse();
      var authored=activities.activities(lid,user,true);
      String safe=json.writeValueAsString(activities.activities(lid,user,false));
      assertThat(safe).doesNotContain("correctAnswer","acceptedAnswers","optionIds");
      for(var a:authored){
        if("QUIZ".equals(a.get("activityType"))) continue;
        JsonNode content=db.json(a.get("contentJson"));
        long aid=ContentStore.id(a,"id");
        if(content.has("correctAnswer")) {
          assertThat(activities.interact(user,aid,json.createObjectNode(),true).get("activityCompleted")).isEqualTo(false);
        }
        activities.interact(user,aid,answer(content.path("correctAnswer")),true);
      }
      var quizActivities=authored.stream().filter(a->"QUIZ".equals(a.get("activityType"))).toList();
      assertThat(quizActivities).hasSize(ends.contains(n)?(n==18?3:2):1);
      for(int i=0;i<quizActivities.size();i++){
        long quiz=ContentStore.id(quizActivities.get(i),"quizId");
        long attempt=ContentStore.id(quizzes.start(user,quiz),"id");
        if(i>0){
          assertThat(quizzes.submit(user,attempt).get("passed")).isEqualTo(false);
          failed++;
          assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(false);
          if(n<18)assertThat(courses.locked(user,ContentStore.id(lessonRows.get(n),"id"))).isFalse();
          assertThat(progress.courseProgress(user,course).get("status")).isNotEqualTo("COMPLETED");
          attempt=ContentStore.id(quizzes.start(user,quiz),"id");
        }
        for(var q:db.rows("SELECT a.question_id,v.correct_answer_json FROM quiz_answers a JOIN question_versions v ON v.id=a.question_version_id WHERE a.attempt_id=?",attempt))
          quizzes.save(user,attempt,ContentStore.id(q,"questionId"),answer(db.json(q.get("correctAnswerJson"))));
        assertThat(quizzes.submit(user,attempt).get("passed")).isEqualTo(true);
      }
      assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(true);
      assertThat(progress.courseProgress(user,course).get("completedLessons")).isEqualTo((long)++completed);
    }
    assertThat(failed).isEqualTo(7);
    assertThat(progress.courseProgress(user,course).get("status")).isEqualTo("COMPLETED");
    assertThat(seed.seed(user)).isEqualTo(course);
    assertThat(db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND passed=true",user)).isEqualTo(25);
  }

  private JsonNode answer(JsonNode key){
    if(key.has("acceptedAnswers"))return json.createObjectNode().put("text",key.path("acceptedAnswers").get(0).asText());
    return key.isMissingNode()?json.createObjectNode():key;
  }
}
