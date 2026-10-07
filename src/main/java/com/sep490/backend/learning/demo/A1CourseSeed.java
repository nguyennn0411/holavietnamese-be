package com.sep490.backend.learning.demo;

import com.fasterxml.jackson.databind.*;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.grammar.GrammarService;
import com.sep490.backend.learning.lesson.ActivityService;
import com.sep490.backend.learning.quiz.*;
import com.sep490.backend.learning.shared.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Re-runnable authoring seed: inserts through the real builders, never overwrites learner history. */
@Service
@RequiredArgsConstructor
public class A1CourseSeed {
  public static final String CODE = "HOLA-A1-MVP";
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final GrammarService grammar;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional
  public long seed(long author) throws Exception {
    // Serializes repeated startup calls without depending on a fixed user/course/lesson ID.
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE",author);
    var existing=db.rows("SELECT id FROM courses WHERE code=?",CODE);
    if(!existing.isEmpty()) return ContentStore.id(existing.getFirst(),"id");
    JsonNode data;
    try(var in=new ClassPathResource("learning/a1-course.json").getInputStream()) { data=json.readTree(in); }
    long course=courses.create(json.treeToValue(data.get("course"),ContentRequests.Course.class),author);
    Long previous=null;
    int lessonNumber=0, moduleNumber=0;
    for(var m:data.get("modules")) {
      moduleNumber++;
      long module=courses.addModule(course,json.treeToValue(m.get("metadata"),ContentRequests.Module.class));
      List<Long> checkpointQuestions=new ArrayList<>();
      long lastLesson=0;
      for(var l:m.get("lessons")) {
        lessonNumber++;
        String code=CODE+"-L"+lessonNumber, slug="hola-a1-"+lessonNumber;
        long lesson=courses.addLesson(module,new ContentRequests.Lesson(code,slug,l.path("titleEn").asText(),
            l.path("descriptionEn").asText(),"NORMAL",20,previous==null?List.of():List.of(previous),
            l.path("objectiveEn").asText(),l.path("titleVi").asText(),l.path("descriptionVi").asText(),l.path("objectiveVi").asText()));
        previous=lesson; lastLesson=lesson;
        activity(lesson,"TEXT","Giới thiệu","Introduction",l.get("introduction"),null,List.of());
        activity(lesson,"VOCABULARY","Từ vựng","Vocabulary",json.createObjectNode().set("items",l.get("vocabulary")),null,List.of());
        var g=l.get("grammar").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)g).put("code",code+"-G").put("slug",slug+"-grammar").put("level","A1");
        long topic=grammar.create(json.treeToValue(g,ContentRequests.Grammar.class));
        grammar.status(topic,"PUBLISHED");
        activity(lesson,"GRAMMAR","Mẫu câu và cách dùng","Patterns and usage",json.createObjectNode(),null,List.of(topic));
        activity(lesson,"DIALOGUE","Hội thoại thực tế","Everyday dialogue",json.createObjectNode().set("lines",l.get("dialogue")),null,List.of());
        activity(lesson,"PRACTICE","Luyện tập","Practice",l.get("practice"),null,List.of());
        long quiz=quiz(course,lesson,"Kiểm tra: "+l.path("titleVi").asText(),"Quiz: "+l.path("titleEn").asText());
        int n=0;
        for(var q:l.get("questions")) {
          long question=questions.create(json.treeToValue(q,ContentRequests.Question.class));
          quizzes.addQuestion(quiz,new ContentRequests.QuizQuestion(question,BigDecimal.ONE));
          if(n==0 || n==2) checkpointQuestions.add(question);
          n++;
        }
        quizzes.status(quiz,"PUBLISHED");
        activity(lesson,"QUIZ","Kiểm tra cuối bài","Lesson quiz",json.createObjectNode(),quiz,List.of());
        courses.lessonStatus(lesson,"PUBLISHED");
      }
      long checkpoint=quiz(course,lastLesson,"Ôn tập học phần "+moduleNumber,"Module "+moduleNumber+" checkpoint");
      for(long question:checkpointQuestions) quizzes.addQuestion(checkpoint,new ContentRequests.QuizQuestion(question,BigDecimal.ONE));
      quizzes.status(checkpoint,"PUBLISHED");
      activity(lastLesson,"QUIZ","Ôn tập học phần","Module checkpoint",json.createObjectNode(),checkpoint,List.of());
    }
    courses.status(course,"PUBLISHED");
    return course;
  }

  private long quiz(long course,long lesson,String vi,String en) {
    return quizzes.create(new ContentRequests.Quiz(en,"Review what you have learned. Pass with at least 70%.",
        "LESSON",course,lesson,BigDecimal.valueOf(70),null,null,false,vi,"Ôn lại nội dung đã học. Đạt từ 70% để vượt qua."));
  }

  private void activity(long lesson,String type,String vi,String en,JsonNode content,Long quiz,List<Long> topics) {
    activities.create(lesson,new ContentRequests.Activity(type,en,"Read and practise before continuing.",content,true,
        BigDecimal.ZERO,"PUBLISHED",quiz,topics,vi,"Đọc và luyện tập trước khi tiếp tục."));
  }
}
