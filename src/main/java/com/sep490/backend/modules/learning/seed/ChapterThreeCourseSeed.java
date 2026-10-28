package com.sep490.backend.modules.learning.seed;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.backend.modules.learning.service.CourseContentService;
import com.sep490.backend.modules.learning.service.GrammarService;
import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.service.QuestionBankService;
import com.sep490.backend.modules.learning.service.QuizService;
import com.sep490.backend.modules.learning.store.ContentStore;
import com.sep490.backend.modules.learning.dto.request.ContentRequests;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Original, opt-in Chapter 3 restaurant curriculum persisted through the learning authoring services. */
@Service
@RequiredArgsConstructor
public class ChapterThreeCourseSeed {
  public static final String CODE = "VI-RESTAURANT-CH03";
  private final ContentStore db;
  private final ObjectMapper json;
  private final CourseContentService courses;
  private final GrammarService grammar;
  private final ActivityService activities;
  private final QuestionBankService questions;
  private final QuizService quizzes;

  @Transactional(rollbackFor = Exception.class)
  public long seed(long author) throws Exception {
    db.one("SELECT id FROM users WHERE id=? FOR UPDATE", author);
    var existing=db.rows("SELECT id FROM courses WHERE code=?",CODE);
    if(!existing.isEmpty()) {
      long id=ContentStore.id(existing.getFirst(),"id");
      if(db.count("SELECT count(*) FROM course_units WHERE course_id=?",id)!=6
          ||db.count("SELECT count(*) FROM lessons WHERE course_id=?",id)!=18
          ||db.count("SELECT count(*) FROM quizzes WHERE course_id=?",id)!=25)
        throw new IllegalStateException("Existing Chapter 3 course differs; migrate explicitly to preserve learner history.");
      return id;
    }
    JsonNode data;
    try(var in=new ClassPathResource("learning/chapter-three-course.json").getInputStream()) {data=json.readTree(in);}
    validate(data);
    long course=courses.create(json.treeToValue(data.get("course"),ContentRequests.Course.class),author);
    Long finalLesson=null;
    int moduleNumber=0;
    for(var moduleData:data.path("modules")) {
      ++moduleNumber;
      long module=courses.addModule(course,json.treeToValue(moduleData.get("metadata"),ContentRequests.Module.class));
      List<List<Long>> moduleQuestionIds=new ArrayList<>();
      long moduleLast=0;
      for(var lessonData:moduleData.path("lessons")) {
        int n=lessonData.path("number").asInt();
        long lesson=courses.addLesson(module,new ContentRequests.Lesson(CODE+"-L"+n,"chapter-three-lesson-"+n,
            lessonData.path("titleEn").asText(),lessonData.path("descriptionEn").asText(),n==18?"REVIEW":"NORMAL",
            lessonData.path("estimatedMinutes").asInt(),List.of(),lessonData.path("objectiveEn").asText(),
            lessonData.path("titleVi").asText(),lessonData.path("descriptionVi").asText(),lessonData.path("objectiveVi").asText()));
        finalLesson=lesson;
        moduleLast=lesson;
        Long grammarId=lessonData.path("grammar").isObject()?createGrammar(lessonData.path("grammar"),n):null;
        for(var activity:lessonData.path("activities")) {
          ObjectNode payload=activity.deepCopy();
          payload.put("isRequired",true).put("maxScore",0).put("status","PUBLISHED");
          if("GRAMMAR".equals(payload.path("activityType").asText()))
            payload.set("grammarTopicIds",json.valueToTree(List.of(grammarId)));
          activities.create(lesson,json.treeToValue(payload,ContentRequests.Activity.class));
        }
        long quiz=quiz(course,lesson,false,"Kiểm tra: "+lessonData.path("titleVi").asText(),"Quiz: "+lessonData.path("titleEn").asText());
        List<Long> ids=new ArrayList<>();
        for(var q:lessonData.path("questions"))ids.add(addQuestion(quiz,q));
        moduleQuestionIds.add(ids);
        publishQuiz(lesson,quiz,"Kiểm tra cuối bài","Lesson quiz");
        courses.lessonStatus(lesson,"PUBLISHED");
      }
      long checkpoint=quiz(course,moduleLast,false,"Ôn tập module "+moduleNumber,"Module "+moduleNumber+" checkpoint");
      for(var ids:moduleQuestionIds)for(long id:ids.subList(0,Math.min(3,ids.size())))
        quizzes.addQuestion(checkpoint,new ContentRequests.QuizQuestion(id,BigDecimal.ONE));
      publishQuiz(moduleLast,checkpoint,"Kiểm tra cuối module","Module checkpoint");
    }
    long finalQuiz=quiz(course,finalLesson,true,"Đánh giá cuối khóa Chương 3","Chapter 3 final assessment");
    for(var q:data.path("finalQuestions"))addQuestion(finalQuiz,q);
    publishQuiz(finalLesson,finalQuiz,"Kiểm tra cuối khóa","Final course assessment");
    db.update("UPDATE courses SET final_assessment_quiz_id=? WHERE id=?",finalQuiz,course);
    courses.status(course,"PUBLISHED");
    return course;
  }

  private long createGrammar(JsonNode g,int n) {
    List<ContentRequests.Example> examples=new ArrayList<>();
    for(var e:g.path("examples"))examples.add(new ContentRequests.Example(e.path("vietnameseText").asText(),e.path("translation").asText(),null));
    long id=grammar.create(new ContentRequests.Grammar(CODE+"-G"+n,"chapter-three-grammar-"+n,
        g.path("titleEn").asText(),g.path("structureVi").asText(),g.path("explanationEn").asText(),
        null,"A1",examples,null,null,g.path("titleVi").asText(),null,g.path("explanationVi").asText(),
        null,g.path("structureEn").asText()));
    grammar.status(id,"PUBLISHED");
    return id;
  }

  private long addQuestion(long quiz,JsonNode q) throws Exception {
    ObjectNode payload=q.deepCopy();payload.remove("points");
    long id=questions.create(json.treeToValue(payload,ContentRequests.Question.class));
    quizzes.addQuestion(quiz,new ContentRequests.QuizQuestion(id,BigDecimal.valueOf(q.path("points").asInt(1))));
    return id;
  }

  private long quiz(long course,long lesson,boolean last,String vi,String en) {
    return quizzes.create(new ContentRequests.Quiz(en,"Score at least 70%. Review answers and retry if needed.",
        last?"COURSE":"LESSON",course,lesson,BigDecimal.valueOf(70),null,null,false,vi,
        "Đạt từ 70%. Xem lại đáp án và làm lại khi cần."));
  }

  private void publishQuiz(long lesson,long quiz,String vi,String en) {
    quizzes.status(quiz,"PUBLISHED");
    activities.create(lesson,new ContentRequests.Activity("QUIZ",en,null,json.createObjectNode(),true,
        BigDecimal.ZERO,"PUBLISHED",quiz,List.of(),vi,null));
  }

  private void validate(JsonNode data) throws Exception {
    if(data.path("modules").size()!=6||data.path("finalQuestions").size()!=50)
      throw new IllegalStateException("Expected six modules and 50 final questions");
    int count=0,grammars=0;
    for(var m:data.path("modules"))for(var l:m.path("lessons")) {
      if(l.path("number").asInt()!=++count||l.path("questions").size()!=5||l.path("activities").size()!=7)
        throw new IllegalStateException("Incomplete or unordered lesson "+count);
      for(String k:List.of("titleVi","titleEn","descriptionVi","descriptionEn","objectiveVi","objectiveEn"))
        if(l.path(k).asText().isBlank())throw new IllegalStateException("Missing "+k+" in lesson "+count);
      if(l.path("grammar").isObject()) {
        grammars++;
        if(l.path("grammar").path("examples").size()<3)throw new IllegalStateException("Incomplete grammar examples");
      }
    }
    if(count!=18||grammars!=16)throw new IllegalStateException("Expected 18 lessons and 16 grammar topics");
    Map<String,Integer> totals=new HashMap<>();
    for(var q:data.path("finalQuestions"))totals.merge(q.path("topic").asText(),q.path("points").asInt(),Integer::sum);
    if(!totals.equals(Map.of("CH03_MODULE_1",16,"CH03_MODULE_2",16,"CH03_MODULE_3",16,
        "CH03_MODULE_4",16,"CH03_MODULE_5",18,"CH03_MODULE_6",18)))
      throw new IllegalStateException("Final assessment weights differ from authoring source");
    validateAudio(data);
  }

  private void validateAudio(JsonNode node) throws Exception {
    if(node.isObject()&&node.hasNonNull("audioUrl")) {
      String url=node.path("audioUrl").asText();
      if(!url.startsWith("/media/pronunciation/chapter03-"))throw new IllegalStateException("Unexpected audio path "+url);
      try(var in=new ClassPathResource("static"+url).getInputStream()) {
        byte[] bytes=in.readNBytes(12);
        if(bytes.length!=12||!new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")
            ||!new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WAVE"))
          throw new IllegalStateException("Invalid WAV "+url);
      }
    }
    if(node.isContainerNode())for(var child:node)validateAudio(child);
  }
}
