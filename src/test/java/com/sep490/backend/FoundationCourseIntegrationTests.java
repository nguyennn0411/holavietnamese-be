package com.sep490.backend;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.demo.FoundationCourseSeed;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.progress.ActivityProgressService;
import com.sep490.backend.learning.quiz.QuizService;
import com.sep490.backend.learning.shared.*;
import com.sep490.backend.service.CourseService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FoundationCourseIntegrationTests {
  @Autowired ContentStore db;
  @Autowired ObjectMapper json;
  @Autowired FoundationCourseSeed seed;
  @Autowired CourseService enrollment;
  @Autowired CourseContentService courses;
  @Autowired ActivityService activities;
  @Autowired ActivityProgressService progress;
  @Autowired QuizService quizzes;
  @Autowired MockMvc mvc;

  @Test void entirePersistedCurriculumGradesResumesUnlocksAndRequiresFinal() throws Exception {
    long user = db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,'test',true)", UUID.randomUUID()+"@foundation.test");
    var principal = new LearnerPrincipal(user, "foundation@test.local", "", true);
    long course = seed.seed(user);
    assertThat(seed.seed(user)).isEqualTo(course);
    assertThat(db.count("SELECT count(*) FROM courses WHERE code=?", FoundationCourseSeed.CODE)).isEqualTo(1);
    assertThat(db.count("SELECT count(*) FROM course_units WHERE course_id=?", course)).isEqualTo(10);
    assertThat(db.count("SELECT count(*) FROM quizzes WHERE course_id=?", course)).isEqualTo(51);
    assertThat(db.count("SELECT count(*) FROM lesson_prerequisites r JOIN lessons l ON r.lesson_id=l.id WHERE l.course_id=?", course)).isEqualTo(47);
    long finalQuiz = ContentStore.id(db.one("SELECT final_assessment_quiz_id FROM courses WHERE id=?", course), "finalAssessmentQuizId");
    assertThat(db.rows("SELECT q.topic,SUM(qq.points) AS weight FROM questions q JOIN quiz_questions qq ON qq.question_id=q.id WHERE qq.quiz_id=? GROUP BY q.topic", finalQuiz))
        .extracting(r -> r.get("topic")+":"+new BigDecimal(r.get("weight").toString()).intValue())
        .containsExactlyInAnyOrder("LISTENING:20", "SOUND_RECOGNITION:15", "VOCABULARY:20", "READING:20", "GRAMMAR:15", "COMMUNICATION:10");
    enrollment.enroll(user, course);
    var lessons = db.rows("SELECT * FROM lessons WHERE course_id=? ORDER BY lesson_order", course);
    assertThat(lessons).hasSize(48);
    long first = ContentStore.id(lessons.getFirst(), "id"), second = ContentStore.id(lessons.get(1), "id");
    mvc.perform(get("/api/lessons/"+second).with(user(principal))).andExpect(status().isForbidden());
    mvc.perform(get("/api/courses/"+course).with(user(principal)))
        .andExpect(jsonPath("$.titleVi").value("Tiếng Việt nền tảng A0–A1"))
        .andExpect(jsonPath("$.modules.length()").value(10))
        .andExpect(jsonPath("$.totalLessons").value(48));
    Set<String> vocabulary = new HashSet<>();
    int completed = 0;
    for (var l : lessons) {
      long lid = ContentStore.id(l,"id");
      assertThat(courses.locked(user,lid)).isFalse();
      var authored = db.rows("SELECT * FROM lesson_activities WHERE lesson_id=? ORDER BY order_index",lid);
      assertThat(authored).hasSizeBetween(7,8);
      assertThat(authored.stream().map(a->a.get("activityType"))).contains("TEXT","VOCABULARY","AUDIO","DIALOGUE","PRACTICE","QUIZ");
      String learnerPayload = db.encode(activities.activities(lid,user,false));
      assertThat(learnerPayload).doesNotContain("correctAnswer", "acceptedAnswers", "isCorrect", "lorem ipsum", "sample question", "example lesson");
      for (var a : authored) {
        assertThat(a.get("titleVi")).isNotNull();
        var content=db.json(a.get("contentJson"));
        if (a.get("activityType").equals("VOCABULARY")) for (var w:content.path("items")) {
          for(String key:List.of("wordVi","meaningEn","exampleVi","exampleEn")) assertThat(w.path(key).asText()).isNotBlank();
          vocabulary.add(w.path("wordVi").asText());
        }
        if (a.get("activityType").equals("QUIZ")) continue;
        // A passing quiz alone must not complete the first lesson.
        if (lid == first && a.get("activityType").equals("TEXT")) continue;
        long aid=ContentStore.id(a,"id");
        if (a.get("activityType").equals("PRACTICE")) {
          assertThat(activities.interact(user,aid,json.createObjectNode(),true).get("activityCompleted")).isEqualTo(false);
        }
        assertThat(activities.interact(user,aid,answer(content.path("correctAnswer")),true).get("activityCompleted")).isEqualTo(true);
      }
      assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(false);
      var checks=authored.stream().filter(a->a.get("activityType").equals("QUIZ")).toList();
      for(var a:checks) {
        long quiz=ContentStore.id(a,"quizId");
        long attempt=ContentStore.id(quizzes.start(user,quiz),"id");
        assertThat(ContentStore.id(quizzes.start(user,quiz),"id")).isEqualTo(attempt);
        assertThat(db.encode(quizzes.attempt(user,attempt,false))).doesNotContain("correctAnswer","acceptedAnswers","isCorrect","explanationVi");
        if(lid==first || quiz==finalQuiz) {
          answerCorrect(user,attempt,quiz==finalQuiz?13:3);
          var failed=quizzes.submit(user,attempt);
          assertThat(failed.get("passed")).isEqualTo(false);
          assertThat(failed.get("resultBandEn")).isEqualTo("Almost There");
          assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(false);
          assertThat(progress.courseProgress(user,course).get("status")).isEqualTo("IN_PROGRESS");
          if(lid==first) {
            assertThat(courses.locked(user,second)).isTrue();
            mvc.perform(post("/api/lessons/"+first+"/complete").with(user(principal)).with(csrf())).andExpect(status().isConflict());
            assertThat(seed.seed(user)).isEqualTo(course);
            assertThat(progress.lessonProgress(user,lid).get("completedActivities")).isEqualTo(5L);
          }
          attempt=ContentStore.id(quizzes.start(user,quiz),"id");
        }
        // Exactly 70% passes the final; earlier checks use a perfect score.
        answerCorrect(user,attempt,quiz==finalQuiz?14:100);
        var passed=quizzes.submit(user,attempt);
        assertThat(passed.get("passed")).isEqualTo(true);
        assertThat(passed.get("resultBandEn")).isEqualTo(quiz==finalQuiz?"Passed":"Excellent");
      }
      if (lid == first) {
        assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(false);
        assertThat(courses.locked(user,second)).isTrue();
        long introduction=ContentStore.id(authored.getFirst(),"id");
        activities.interact(user,introduction,json.createObjectNode(),true);
      }
      assertThat(progress.lessonProgress(user,lid).get("completed")).isEqualTo(true);
      assertThat(progress.courseProgress(user,course).get("completedLessons")).isEqualTo((long)++completed);
    }
    assertThat(vocabulary).hasSizeGreaterThanOrEqualTo(300);
    assertThat(progress.courseProgress(user,course).get("status")).isEqualTo("COMPLETED");
    assertThat(progress.courseProgress(user,course).get("progressPercentage")).isEqualTo(100L);
    assertThat(seed.seed(user)).isEqualTo(course);
    assertThat(db.count("SELECT count(*) FROM quiz_attempts WHERE user_id=? AND passed=true",user)).isEqualTo(51);
    assertThat(db.count("SELECT count(*) FROM learning_events WHERE user_id=? AND event_type='COURSE_COMPLETED'",user)).isEqualTo(1);
  }

  @Test void audioAssetsAreRealWaveFilesAndAllContentIsBilingual() throws Exception {
    JsonNode data;
    try(var in=new ClassPathResource("learning/foundation-course.json").getInputStream()){data=json.readTree(in);}
    for(var m:data.path("modules")) for(var l:m.path("lessons")) {
      for(String section:List.of("introduction","pattern")) for(String key:List.of("textVi","textEn")) assertThat(l.path(section).path(key).asText()).isNotBlank();
      checkAudio(l.path("audio").path("audioUrl").asText());
      for(var q:l.path("questions")) {
        for(String key:List.of("promptVi","promptEn","explanationVi","explanationEn")) assertThat(q.path(key).asText()).isNotBlank();
        for(var o:q.path("options")) for(String key:List.of("textVi","textEn")) assertThat(o.path(key).asText()).isNotBlank();
        if(q.path("questionType").asText().equals("LISTENING")) checkAudio(q.path("audioUrl").asText());
      }
    }
    mvc.perform(get("/media/foundation/lesson-1.wav")).andExpect(status().isOk());
  }

  private void checkAudio(String url) throws Exception {
    try(var in=new ClassPathResource("static"+url).getInputStream()) {
      byte[] bytes=in.readAllBytes();
      assertThat(bytes.length).isGreaterThan(1000);
      assertThat(new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("RIFF");
      assertThat(new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("WAVE");
    }
  }
  private JsonNode answer(JsonNode key) {
    if(key.has("acceptedAnswers"))return json.createObjectNode().put("text",key.path("acceptedAnswers").get(0).asText());
    return key.isMissingNode()?json.createObjectNode():key;
  }
  private void answerCorrect(long user,long attempt,int limit) {
    int n=0;
    for(var q:db.rows("SELECT a.question_id,v.correct_answer_json FROM quiz_answers a JOIN question_versions v ON v.id=a.question_version_id WHERE a.attempt_id=? ORDER BY a.order_index",attempt)) {
      if(n++>=limit)break;
      quizzes.save(user,attempt,ContentStore.id(q,"questionId"),answer(db.json(q.get("correctAnswerJson"))));
    }
  }
}
