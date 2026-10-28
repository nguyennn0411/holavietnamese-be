package com.sep490.backend.modules.admin.importing.service;
import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.service.CourseContentService;
import com.sep490.backend.modules.learning.service.QuestionBankService;
import com.sep490.backend.modules.learning.service.QuestionScoring;
import com.sep490.backend.modules.learning.service.QuizService;
import com.sep490.backend.modules.learning.service.support.ContentRules;
import com.sep490.backend.modules.learning.store.ContentStore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.learning.dto.request.ContentRequests;
import com.sep490.backend.common.exception.ContentException;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course Studio workbook. All imports create a new draft through the same authoring services as the UI. */
@Service
@RequiredArgsConstructor
public class CourseWorkbookService {
  private final ContentStore db;
  private final CourseContentService courses;
  private final ActivityService activities;
  private final QuizService quizzes;
  private final QuestionBankService questions;
  private final QuestionScoring scoring;
  private final ObjectMapper json;

  private static final LinkedHashMap<String, List<String>> COLUMNS = new LinkedHashMap<>();
  static {
    COLUMNS.put("COURSE", List.of("code","slug","titleEn","titleVi","descriptionEn","descriptionVi","level","estimatedMinutes","learningOutcomes","isFree","coverUrl"));
    COLUMNS.put("MODULES", List.of("moduleKey","titleEn","titleVi","descriptionEn","descriptionVi"));
    COLUMNS.put("LESSONS", List.of("lessonKey","moduleKey","code","slug","titleEn","titleVi","descriptionEn","descriptionVi","lessonType","estimatedMinutes","learningObjectiveEn","learningObjectiveVi","prerequisiteKeys"));
    COLUMNS.put("QUIZZES", List.of("quizKey","lessonKey","quizType","titleEn","titleVi","descriptionEn","descriptionVi","passingScore","timeLimitMinutes","maxAttempts","randomizeQuestions","isFinal"));
    COLUMNS.put("QUESTIONS", List.of("questionKey","quizKey","questionType","promptEn","promptVi","explanationEn","explanationVi","difficulty","topic","optionsJson","correctAnswerJson","points","imageUrl","audioUrl"));
    COLUMNS.put("ACTIVITIES", List.of("lessonKey","activityType","titleEn","titleVi","instructionEn","instructionVi","contentJson","isRequired","maxScore","quizKey","mediaFile","grammarTopicIds"));
  }
  private record Book(Map<String,List<Map<String,String>>> sheets, List<String> errors) {
    List<Map<String,String>> rows(String name) { return sheets.getOrDefault(name,List.of()); }
  }

  public byte[] template() {
    var data=empty();
    data.get("COURSE").add(row("COURSE","EXCEL-CAFE-A1","excel-cafe-a1","Vietnamese at the café","Tiếng Việt ở quán cà phê","A complete beginner course for ordering drinks, asking prices, and paying at a Vietnamese café.","Khóa học nhập môn đầy đủ về gọi đồ uống, hỏi giá và thanh toán tại quán cà phê Việt Nam.","A1","90","Gọi đồ uống, hỏi giá, xác nhận đơn và thanh toán bằng tiếng Việt.","true","/media/tv123/images/scans/cover.jpg"));
    data.get("MODULES").add(row("MODULES","M1","Ordering at the café","Gọi món tại quán cà phê","Learn the words and sentence patterns needed to order.","Học từ vựng và mẫu câu cần thiết để gọi món."));
    data.get("MODULES").add(row("MODULES","M2","Prices and payment","Giá cả và thanh toán","Ask for the price, confirm the bill, and pay politely.","Hỏi giá, xác nhận hóa đơn và thanh toán lịch sự."));
    data.get("LESSONS").add(row("LESSONS","L1","M1","EXCEL-CAFE-A1-L1","excel-cafe-a1-goi-do-uong","Ordering a drink","Gọi đồ uống","Practice greeting staff and ordering a drink.","Luyện chào nhân viên và gọi một đồ uống.","CONVERSATION","40","Order a drink using a polite complete sentence.","Gọi một đồ uống bằng câu đầy đủ và lịch sự.",""));
    data.get("LESSONS").add(row("LESSONS","L2","M2","EXCEL-CAFE-A1-L2","excel-cafe-a1-thanh-toan","Asking the price and paying","Hỏi giá và thanh toán","Practice asking the price and requesting the bill.","Luyện hỏi giá và yêu cầu thanh toán.","CONVERSATION","50","Ask for a price and complete a simple payment conversation.","Hỏi giá và hoàn thành hội thoại thanh toán đơn giản.","L1"));
    data.get("QUIZZES").add(row("QUIZZES","Q1","L1","LESSON","Ordering check","Kiểm tra gọi món","Check the ordering expressions.","Kiểm tra mẫu câu gọi món.","70","10","3","false","false"));
    data.get("QUIZZES").add(row("QUIZZES","Q2","L2","LESSON","Payment check","Kiểm tra thanh toán","Check the price and payment expressions.","Kiểm tra mẫu câu hỏi giá và thanh toán.","70","10","3","false","false"));
    data.get("QUIZZES").add(row("QUIZZES","QF","L2","COURSE","Final café assessment","Bài kiểm tra cuối khóa","Review all course outcomes.","Ôn tập toàn bộ mục tiêu khóa học.","70","15","3","true","true"));
    String drinkOptions="[{\"id\":\"a\",\"textEn\":\"Coffee\",\"textVi\":\"Cà phê\"},{\"id\":\"b\",\"textEn\":\"Tea\",\"textVi\":\"Trà\"}]";
    String drinkAnswer="{\"optionIds\":[\"a\"]}";
    String priceOptions="[{\"id\":\"a\",\"textEn\":\"How much is it?\",\"textVi\":\"Bao nhiêu tiền?\"},{\"id\":\"b\",\"textEn\":\"What is your name?\",\"textVi\":\"Bạn tên gì?\"}]";
    String priceAnswer="{\"optionIds\":[\"a\"]}";
    data.get("QUESTIONS").add(row("QUESTIONS","X1","Q1","MULTIPLE_CHOICE","Which option means coffee?","Từ nào có nghĩa là coffee?","Cà phê means coffee.","Cà phê có nghĩa là coffee.","EASY","cafe",drinkOptions,drinkAnswer,"1","",""));
    data.get("QUESTIONS").add(row("QUESTIONS","X2","Q2","MULTIPLE_CHOICE","How do you ask for the price?","Bạn hỏi giá như thế nào?","Use Bao nhiêu tiền? to ask the price.","Dùng Bao nhiêu tiền? để hỏi giá.","EASY","payment",priceOptions,priceAnswer,"1","",""));
    data.get("QUESTIONS").add(row("QUESTIONS","X1","QF","MULTIPLE_CHOICE","Which option means coffee?","Từ nào có nghĩa là coffee?","Cà phê means coffee.","Cà phê có nghĩa là coffee.","EASY","cafe",drinkOptions,drinkAnswer,"1","",""));
    data.get("QUESTIONS").add(row("QUESTIONS","X2","QF","MULTIPLE_CHOICE","How do you ask for the price?","Bạn hỏi giá như thế nào?","Use Bao nhiêu tiền? to ask the price.","Dùng Bao nhiêu tiền? để hỏi giá.","EASY","payment",priceOptions,priceAnswer,"1","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L1","TEXT","Ordering dialogue","Hội thoại gọi món","Read and practice the dialogue.","Đọc và luyện tập hội thoại.","{\"textVi\":\"Nhân viên: Xin chào. Bạn uống gì?\\nKhách: Cho tôi một cà phê sữa, cảm ơn.\",\"textEn\":\"Staff: Hello. What would you like to drink?\\nCustomer: One milk coffee, please.\"}","true","0","","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L1","IMAGE","Café vocabulary picture","Ảnh từ vựng quán cà phê","Observe the picture and name familiar items.","Quan sát ảnh và gọi tên các đồ vật quen thuộc.","{\"imageUrl\":\"/media/tv123/images/illustrations/lesson-01-1.jpg\",\"altVi\":\"Minh họa bài học\",\"altEn\":\"Lesson illustration\"}","false","0","","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L1","QUIZ","Ordering quiz","Quiz gọi món","Complete the lesson quiz.","Hoàn thành quiz cuối bài.","{}","true","0","Q1","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L2","TEXT","Payment dialogue","Hội thoại thanh toán","Read and practice the dialogue.","Đọc và luyện tập hội thoại.","{\"textVi\":\"Khách: Bao nhiêu tiền?\\nNhân viên: Bốn mươi nghìn đồng.\\nKhách: Cho tôi thanh toán.\",\"textEn\":\"Customer: How much is it?\\nStaff: Forty thousand dong.\\nCustomer: I would like to pay.\"}","true","0","","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L2","QUIZ","Payment quiz","Quiz thanh toán","Complete the lesson quiz.","Hoàn thành quiz cuối bài.","{}","true","0","Q2","",""));
    data.get("ACTIVITIES").add(row("ACTIVITIES","L2","QUIZ","Final course assessment","Bài kiểm tra cuối khóa","Complete the final assessment.","Hoàn thành bài kiểm tra cuối khóa.","{}","true","0","QF","",""));
    return write(data);
  }

  public Map<String,Object> validate(byte[] bytes,String filename) {
    return report(read(bytes,filename));
  }

  @Transactional
  public Map<String,Object> importDraft(byte[] bytes,String filename,long author) {
    Book book=read(bytes,filename);
    Map<String,Object> report=report(book);
    if(!Boolean.TRUE.equals(report.get("valid"))) throw ContentException.invalid(String.join("; ",(List<String>)report.get("errors")));
    var c=book.rows("COURSE").getFirst();
    String code=c.get("code");
    if(db.count("SELECT count(*) FROM courses WHERE code=? OR slug=?",code,c.get("slug"))>0)
      throw ContentException.conflict("COURSE_EXISTS","Mã hoặc đường dẫn khóa học đã tồn tại. Hãy đổi code và slug trong Excel.");
    long courseId=courses.create(new ContentRequests.Course(code,c.get("slug"),c.get("titleEn"),c.get("descriptionEn"),c.get("level"),blank(c.get("coverUrl")),integer(c,"estimatedMinutes"),c.get("learningOutcomes"),yes(c,"isFree"),blank(c.get("titleVi")),blank(c.get("descriptionVi"))),author);
    Map<String,Long> modules=new HashMap<>(),lessons=new HashMap<>(),quizIds=new HashMap<>();
    for(var m:book.rows("MODULES")) modules.put(m.get("moduleKey"),courses.addModule(courseId,new ContentRequests.Module(m.get("titleEn"),m.get("descriptionEn"),blank(m.get("titleVi")),blank(m.get("descriptionVi")))));
    for(var l:book.rows("LESSONS")) lessons.put(l.get("lessonKey"),courses.addLesson(modules.get(l.get("moduleKey")),new ContentRequests.Lesson(l.get("code"),l.get("slug"),l.get("titleEn"),l.get("descriptionEn"),l.get("lessonType"),integer(l,"estimatedMinutes"),List.of(),l.get("learningObjectiveEn"),blank(l.get("titleVi")),blank(l.get("descriptionVi")),blank(l.get("learningObjectiveVi")))));
    for(var l:book.rows("LESSONS")) if(!l.get("prerequisiteKeys").isBlank())
      courses.editLesson(lessons.get(l.get("lessonKey")),new ContentRequests.Lesson(l.get("code"),l.get("slug"),l.get("titleEn"),l.get("descriptionEn"),l.get("lessonType"),integer(l,"estimatedMinutes"),Arrays.stream(l.get("prerequisiteKeys").split(";")).map(String::strip).map(lessons::get).toList(),l.get("learningObjectiveEn"),blank(l.get("titleVi")),blank(l.get("descriptionVi")),blank(l.get("learningObjectiveVi"))));
    for(var q:book.rows("QUIZZES")) {
      Long lesson=lessons.get(q.get("lessonKey"));
      long quiz=quizzes.create(new ContentRequests.Quiz(q.get("titleEn"),q.get("descriptionEn"),q.get("quizType"),courseId,lesson,new BigDecimal(q.get("passingScore")),optionalInt(q,"timeLimitMinutes"),optionalInt(q,"maxAttempts"),yes(q,"randomizeQuestions"),blank(q.get("titleVi")),blank(q.get("descriptionVi"))));
      quizIds.put(q.get("quizKey"),quiz);
      if(yes(q,"isFinal")) courses.setFinalAssessment(courseId,quiz);
    }
    Map<String,Long> questionIds=new HashMap<>();
    for(var q:book.rows("QUESTIONS")) {
      long question=questionIds.computeIfAbsent(q.get("questionKey"),ignored->questions.create(new ContentRequests.Question(q.get("questionType"),q.get("promptEn"),q.get("explanationEn"),q.get("difficulty"),blank(q.get("topic")),blank(q.get("imageUrl")),blank(q.get("audioUrl")),"PUBLISHED",parse(q.get("correctAnswerJson")),parse(q.get("optionsJson")),blank(q.get("promptVi")),blank(q.get("explanationVi"))),author));
      quizzes.addQuestion(quizIds.get(q.get("quizKey")),new ContentRequests.QuizQuestion(question,new BigDecimal(q.get("points"))));
    }
    for(long quiz:quizIds.values()) quizzes.status(quiz,"PUBLISHED");
    for(var a:book.rows("ACTIVITIES")) {
      JsonNode content=parse(a.get("contentJson"));
      if(!a.get("mediaFile").isBlank()) ((com.fasterxml.jackson.databind.node.ObjectNode)content).put("pendingFile",a.get("mediaFile"));
      List<Long> topics=a.get("grammarTopicIds").isBlank()?List.of():Arrays.stream(a.get("grammarTopicIds").split(";")).map(String::strip).map(Long::parseLong).toList();
      activities.create(lessons.get(a.get("lessonKey")),new ContentRequests.Activity(a.get("activityType"),a.get("titleEn"),a.get("instructionEn"),content,yes(a,"isRequired"),new BigDecimal(a.get("maxScore")),"PUBLISHED",quizIds.get(a.get("quizKey")),topics,blank(a.get("titleVi")),blank(a.get("instructionVi"))));
    }
    for(long lesson:lessons.values()) courses.lessonStatus(lesson,"PUBLISHED");
    var result=new LinkedHashMap<>(report);result.put("courseId",courseId);result.put("status","DRAFT");
    return result;
  }

  private Map<String,Object> report(Book book) {
    List<String> errors=new ArrayList<>(book.errors());
    if(book.rows("COURSE").size()!=1) errors.add("COURSE: cần đúng một dòng khóa học.");
    Set<String> moduleKeys=keys(book,"MODULES","moduleKey",errors),lessonKeys=keys(book,"LESSONS","lessonKey",errors),quizKeys=keys(book,"QUIZZES","quizKey",errors);
    Map<String,String> quizLessons=new HashMap<>();for(var row:book.rows("QUIZZES"))quizLessons.put(row.get("quizKey"),row.get("lessonKey"));
    if(moduleKeys.isEmpty()) errors.add("MODULES: cần ít nhất một học phần.");
    if(lessonKeys.isEmpty()) errors.add("LESSONS: cần ít nhất một bài học.");
    Set<String> lessonCodes=new HashSet<>(),slugs=new HashSet<>();
    for(var c:book.rows("COURSE")) {
      required(c,errors,"code","slug","titleEn","descriptionEn","level");
      if(!c.get("code").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")) error(c,errors,"code không hợp lệ.");
      if(!c.get("slug").matches("[a-z0-9]+(?:-[a-z0-9]+)*")) error(c,errors,"slug không hợp lệ.");
      if(c.get("slug").length()>200||c.get("titleEn").length()>200||c.get("titleVi").length()>200)error(c,errors,"slug/tên khóa học vượt quá 200 ký tự.");
      if(!ContentRules.LEVELS.contains(c.get("level"))) error(c,errors,"level không hợp lệ.");
      nonnegative(c,"estimatedMinutes",errors);booleanValue(c,"isFree",errors);
      try {ContentRules.url(blank(c.get("coverUrl")));}catch(RuntimeException ex){error(c,errors,"coverUrl không hợp lệ.");}
      if(db.count("SELECT count(*) FROM courses WHERE code=? OR slug=?",c.get("code"),c.get("slug"))>0) error(c,errors,"code hoặc slug đã tồn tại; đổi để nhập thành khóa mới.");
    }
    for(var m:book.rows("MODULES")){required(m,errors,"moduleKey","titleEn");if(m.get("titleEn").length()>200||m.get("titleVi").length()>200)error(m,errors,"tên học phần vượt quá 200 ký tự.");}
    for(var l:book.rows("LESSONS")) {
      required(l,errors,"lessonKey","moduleKey","code","slug","titleEn","lessonType");
      if(!moduleKeys.contains(l.get("moduleKey"))) error(l,errors,"moduleKey không tồn tại.");
      if(!lessonCodes.add(l.get("code").toLowerCase(Locale.ROOT))) error(l,errors,"code bài học trùng.");
      if(!slugs.add(l.get("slug").toLowerCase(Locale.ROOT))) error(l,errors,"slug bài học trùng.");
      if(!l.get("code").matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")||!l.get("slug").matches("[a-z0-9]+(?:-[a-z0-9]+)*")) error(l,errors,"code/slug bài học không hợp lệ.");
      if(l.get("slug").length()>200||l.get("titleEn").length()>200||l.get("titleVi").length()>200)error(l,errors,"slug/tên bài học vượt quá 200 ký tự.");
      if(!ContentRules.LESSON_TYPES.contains(l.get("lessonType"))) error(l,errors,"lessonType không hợp lệ.");
      for(String key:l.get("prerequisiteKeys").split(";"))if(!key.isBlank()&&!lessonKeys.contains(key.strip()))error(l,errors,"prerequisiteKeys chứa bài không tồn tại: "+key);
      nonnegative(l,"estimatedMinutes",errors);
    }
    int pending=0,finalCount=0;
    Map<String,Map<String,String>> questionDefinitions=new HashMap<>();Set<String> quizQuestionPairs=new HashSet<>();
    for(var q:book.rows("QUIZZES")) {
      required(q,errors,"quizKey","quizType","titleEn","passingScore");
      if(!q.get("lessonKey").isBlank()&&!lessonKeys.contains(q.get("lessonKey"))) error(q,errors,"lessonKey không tồn tại.");
      if(!Set.of("LESSON","COURSE","PRACTICE").contains(q.get("quizType"))) error(q,errors,"quizType không hợp lệ.");
      if(q.get("titleEn").length()>200||q.get("titleVi").length()>200)error(q,errors,"tên quiz vượt quá 200 ký tự.");
      if(q.get("quizType").equals("LESSON")&&q.get("lessonKey").isBlank()) error(q,errors,"quiz LESSON cần lessonKey.");
      try {double score=Double.parseDouble(q.get("passingScore"));if(score<0||score>100) throw new NumberFormatException();}catch(NumberFormatException ex){error(q,errors,"passingScore phải từ 0 đến 100.");}
      positiveOptional(q,"timeLimitMinutes",errors);positiveOptional(q,"maxAttempts",errors);
      booleanValue(q,"randomizeQuestions",errors);booleanValue(q,"isFinal",errors);
      if(yes(q,"isFinal")){finalCount++;if(!q.get("quizType").equals("COURSE")) error(q,errors,"quiz cuối khóa phải có quizType COURSE.");}
    }
    if(finalCount>1) errors.add("QUIZZES: chỉ được một quiz cuối khóa.");
    for(var q:book.rows("QUESTIONS")) {
      required(q,errors,"questionKey","quizKey","questionType","promptEn","difficulty","optionsJson","correctAnswerJson","points");
      if(!quizKeys.contains(q.get("quizKey"))) error(q,errors,"quizKey không tồn tại.");
      if(!quizQuestionPairs.add(q.get("quizKey")+"\u001f"+q.get("questionKey")))error(q,errors,"cùng câu hỏi bị lặp trong một quiz.");
      Map<String,String> definition=new HashMap<>(q);definition.remove("_row");definition.remove("quizKey");definition.remove("points");
      Map<String,String> earlier=questionDefinitions.putIfAbsent(q.get("questionKey"),definition);
      if(earlier!=null&&!earlier.equals(definition))error(q,errors,"questionKey dùng lại nhưng nội dung/đáp án khác nhau.");
      if(!Set.of("EASY","MEDIUM","HARD").contains(q.get("difficulty"))) error(q,errors,"difficulty không hợp lệ.");
      try {scoring.validate(q.get("questionType"),parse(q.get("optionsJson")),parse(q.get("correctAnswerJson")));}catch(RuntimeException ex){error(q,errors,"cấu hình đáp án/optionsJson không hợp lệ: "+ex.getMessage());}
      positive(q,"points",errors);
      for(String col:List.of("imageUrl","audioUrl")) try {ContentRules.url(blank(q.get(col)));}catch(RuntimeException ex){error(q,errors,col+" không hợp lệ.");}
      if((q.get("questionType").equals("IMAGE_SELECTION")&&q.get("imageUrl").isBlank())||(q.get("questionType").equals("LISTENING")&&q.get("audioUrl").isBlank())) pending++;
    }
    for(var a:book.rows("ACTIVITIES")) {
      required(a,errors,"lessonKey","activityType","titleEn","contentJson","isRequired","maxScore");
      if(!lessonKeys.contains(a.get("lessonKey"))) error(a,errors,"lessonKey không tồn tại.");
      if(!ContentRules.ACTIVITIES.contains(a.get("activityType"))) error(a,errors,"activityType không hợp lệ.");
      if(a.get("titleEn").length()>200||a.get("titleVi").length()>200)error(a,errors,"tên hoạt động vượt quá 200 ký tự.");
      if(a.get("activityType").equals("QUIZ")&&!quizKeys.contains(a.get("quizKey"))) error(a,errors,"hoạt động QUIZ cần quizKey hợp lệ.");
      if(!a.get("quizKey").isBlank()&&!quizKeys.contains(a.get("quizKey"))) error(a,errors,"quizKey không tồn tại.");
      if(a.get("activityType").equals("QUIZ")&&!Objects.equals(a.get("lessonKey"),quizLessons.get(a.get("quizKey"))))error(a,errors,"quizKey phải thuộc cùng bài học.");
      if(a.get("activityType").equals("GRAMMAR")&&a.get("grammarTopicIds").isBlank()) error(a,errors,"hoạt động GRAMMAR cần grammarTopicIds; có thể thêm sau trong trình biên soạn.");
      for(String topic:a.get("grammarTopicIds").split(";"))if(!topic.isBlank())try{long id=Long.parseLong(topic.strip());if(db.count("SELECT count(*) FROM grammar_topics WHERE id=?",id)==0)error(a,errors,"grammarTopicIds không tồn tại: "+topic);}catch(NumberFormatException ex){error(a,errors,"grammarTopicIds phải là ID ngăn bằng dấu ;.");}
      booleanValue(a,"isRequired",errors);nonnegativeDecimal(a,"maxScore",errors);
      try {
        JsonNode content=parse(a.get("contentJson"));if(!content.isObject()||a.get("contentJson").length()>100000)throw new IllegalArgumentException();
        for(String key:List.of("imageUrl","audioUrl","videoUrl"))ContentRules.url(content.path(key).asText(null));
        String mediaKey=switch(a.get("activityType")){case "IMAGE"->"imageUrl";case "AUDIO"->"audioUrl";case "VIDEO"->"videoUrl";default->"";};
        if(!mediaKey.isEmpty()&&content.path(mediaKey).asText("").isBlank())pending++;
      }catch(RuntimeException ex){error(a,errors,"contentJson phải là JSON object hợp lệ (URL media dùng http(s) hoặc /media/; tối đa 100 KB).");}
    }
    var counts=new LinkedHashMap<String,Integer>();for(String name:COLUMNS.keySet())counts.put(name,name.equals("QUESTIONS")?questionDefinitions.size():book.rows(name).size());
    counts.put("QUIZ_QUESTION_LINKS",book.rows("QUESTIONS").size());
    return Map.of("valid",errors.isEmpty(),"errors",errors,"counts",counts,"pendingMedia",pending);
  }

  private Book read(byte[] bytes,String filename) {
    var data=empty();List<String> errors=new ArrayList<>();
    if(filename==null||!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")||bytes.length==0||bytes.length>10*1024*1024) return new Book(data,List.of("Chọn file .xlsx không quá 10 MB."));
    try {
      long expanded=0;int entries=0;byte[] buffer=new byte[8192];
      try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
        while(zip.getNextEntry()!=null){if(++entries>2000) throw new IOException();int n;while((n=zip.read(buffer))!=-1)if((expanded+=n)>50L*1024*1024)throw new IOException();}
      }
      if(entries==0) throw new IOException();
      try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
        for(var spec:COLUMNS.entrySet()) {
          Sheet sheet=workbook.getSheet(spec.getKey());if(sheet==null){errors.add("Thiếu sheet "+spec.getKey()+".");continue;}
          if(sheet.getLastRowNum()>2000) {errors.add(spec.getKey()+": tối đa 2000 dòng.");continue;}
          Row header=sheet.getRow(0);if(header==null){errors.add(spec.getKey()+": thiếu hàng tiêu đề.");continue;}
          Map<String,Integer> positions=new HashMap<>();
          for(Cell cell:header){String name=cell.getStringCellValue().strip();if(positions.putIfAbsent(name,cell.getColumnIndex())!=null)errors.add(spec.getKey()+": cột "+name+" bị trùng.");}
          for(String column:spec.getValue())if(!positions.containsKey(column))errors.add(spec.getKey()+": thiếu cột "+column+".");
          for(int i=1;i<=sheet.getLastRowNum();i++){
            Row source=sheet.getRow(i);if(source==null)continue;Map<String,String> value=new LinkedHashMap<>();boolean present=false;
            for(String column:spec.getValue()){
              Cell cell=positions.containsKey(column)?source.getCell(positions.get(column)):null;
              if(cell!=null&&(cell.getCellType()==CellType.FORMULA||cell.getCellType()==CellType.ERROR))errors.add(spec.getKey()+" dòng "+(i+1)+": không dùng công thức/lỗi Excel.");
              String text=cellValue(cell);if(text.length()>32767)errors.add(spec.getKey()+" dòng "+(i+1)+": ô vượt giới hạn Excel 32767 ký tự.");
              value.put(column,text);present|=!text.isBlank();
            }
            if(present){value.put("_row",spec.getKey()+" dòng "+(i+1));data.get(spec.getKey()).add(value);}
          }
        }
      }
    }catch(Exception ex){errors.add("File Excel không hợp lệ hoặc vượt giới hạn kích thước.");}
    return new Book(data,errors);
  }

  private byte[] write(Map<String,List<Map<String,String>>> data) {
    try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
      CellStyle headerStyle=workbook.createCellStyle();Font font=workbook.createFont();font.setBold(true);headerStyle.setFont(font);headerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      Sheet guide=workbook.createSheet("HUONG_DAN");
      String[] lines={"MẪU COURSE STUDIO – một khóa học hoàn chỉnh để nhập thành khóa học NHÁP mới","Mỗi sheet giữ nguyên tên cột. Đổi mã khóa học, slug, mã bài và slug bài học trước mỗi lần nhập.","Liên kết bằng moduleKey, lessonKey, quizKey; questionKey có thể dùng lại ở nhiều quiz.","Mỗi dòng QUESTIONS gắn một questionKey với một quizKey; đáp án của cùng questionKey phải giống nhau.","contentJson, optionsJson, correctAnswerJson là JSON. Mẫu đã có ví dụ hợp lệ.","Mẫu gồm học phần, bài học, hoạt động bắt buộc, quiz từng bài, câu hỏi/đáp án và quiz cuối khóa.","Cột mediaFile chỉ ghi tên tệp cần bổ sung; file ảnh/âm thanh/video không được nhúng vào Excel.","Sau khi nhập, khóa học ở trạng thái DRAFT nhưng nội dung mẫu đã sẵn sàng để kiểm tra và gửi Admin duyệt.","Điền hoặc thay nội dung mẫu, bấm kiểm tra file rồi nhập thành khóa học mới."};
      for(int i=0;i<lines.length;i++)guide.createRow(i).createCell(0).setCellValue(lines[i]);guide.setColumnWidth(0,110*256);
      for(var spec:COLUMNS.entrySet()){
        Sheet sheet=workbook.createSheet(spec.getKey());Row header=sheet.createRow(0);
        for(int i=0;i<spec.getValue().size();i++){Cell cell=header.createCell(i);cell.setCellValue(spec.getValue().get(i));cell.setCellStyle(headerStyle);sheet.setColumnWidth(i,Math.min(48,Math.max(18,spec.getValue().get(i).length()+5))*256);}
        sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(1,data.get(spec.getKey()).size()),0,spec.getValue().size()-1));
        int row=1;for(var values:data.get(spec.getKey())){Row dest=sheet.createRow(row++);for(int i=0;i<spec.getValue().size();i++){String value=values.getOrDefault(spec.getValue().get(i),"");if(value.length()>32767)throw ContentException.invalid(spec.getKey()+": nội dung "+spec.getValue().get(i)+" quá dài cho một ô Excel.");dest.createCell(i).setCellValue(value);}}
      }
      workbook.write(out);return out.toByteArray();
    }catch(IOException ex){throw new IllegalStateException("Cannot create workbook",ex);}
  }
  private Map<String,List<Map<String,String>>> empty(){var map=new LinkedHashMap<String,List<Map<String,String>>>();for(String key:COLUMNS.keySet())map.put(key,new ArrayList<>());return map;}
  private Map<String,String> row(String sheet,String... values){var keys=COLUMNS.get(sheet);var row=new LinkedHashMap<String,String>();for(int i=0;i<keys.size();i++)row.put(keys.get(i),values[i]==null?"":values[i]);return row;}
  private static String blank(String value){return value==null||value.isBlank()?null:value;}
  private static boolean yes(Map<String,String> row,String key){return "true".equalsIgnoreCase(row.getOrDefault(key,""));}
  private static int integer(Map<String,String> row,String key){return Integer.parseInt(row.get(key));}
  private static Integer optionalInt(Map<String,String> row,String key){return row.get(key).isBlank()?null:integer(row,key);}
  private JsonNode parse(String value){try{return json.readTree(value);}catch(Exception ex){throw new IllegalArgumentException("JSON không hợp lệ.");}}
  private static String cellValue(Cell cell){if(cell==null)return "";return switch(cell.getCellType()){case STRING->cell.getStringCellValue().strip();case NUMERIC->BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();case BOOLEAN->String.valueOf(cell.getBooleanCellValue());default->"";};}
  private static void error(Map<String,String> row,List<String> errors,String message){errors.add(row.get("_row")+": "+message);}
  private static void required(Map<String,String> row,List<String> errors,String... fields){for(String f:fields)if(row.getOrDefault(f,"").isBlank())error(row,errors,"thiếu "+f+".");}
  private static Set<String> keys(Book book,String sheet,String key,List<String> errors){Set<String> set=new HashSet<>(),folded=new HashSet<>();for(var row:book.rows(sheet)){String value=row.getOrDefault(key,"");if(value.isBlank())error(row,errors,"thiếu "+key+".");else {if(!folded.add(value.toLowerCase(Locale.ROOT)))error(row,errors,key+" bị trùng.");set.add(value);}}return set;}
  private static void nonnegative(Map<String,String> row,String key,List<String> errors){try{if(new BigDecimal(row.get(key)).signum()<0||new BigDecimal(row.get(key)).compareTo(new BigDecimal("1000000"))>0||!row.get(key).matches("[0-9]+"))throw new NumberFormatException();}catch(Exception ex){error(row,errors,key+" phải là số nguyên từ 0 đến 1000000.");}}
  private static void positiveOptional(Map<String,String> row,String key,List<String> errors){if(row.get(key).isBlank())return;nonnegative(row,key,errors);if("0".equals(row.get(key)))error(row,errors,key+" phải lớn hơn 0.");}
  private static void nonnegativeDecimal(Map<String,String> row,String key,List<String> errors){try{BigDecimal n=new BigDecimal(row.get(key));if(n.signum()<0||n.compareTo(new BigDecimal("1000000"))>0)throw new NumberFormatException();}catch(Exception ex){error(row,errors,key+" phải từ 0 đến 1000000.");}}
  private static void positive(Map<String,String> row,String key,List<String> errors){try{BigDecimal n=new BigDecimal(row.get(key));if(n.signum()<=0||n.compareTo(new BigDecimal("10000"))>0)throw new NumberFormatException();}catch(Exception ex){error(row,errors,key+" phải lớn hơn 0 và không quá 10000.");}}
  private static void booleanValue(Map<String,String> row,String key,List<String> errors){if(!Set.of("true","false").contains(row.getOrDefault(key,"").toLowerCase(Locale.ROOT)))error(row,errors,key+" phải là true hoặc false.");}
}
