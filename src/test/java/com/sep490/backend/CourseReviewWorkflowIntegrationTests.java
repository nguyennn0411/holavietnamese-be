package com.sep490.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.learning.course.CourseContentService;
import com.sep490.backend.learning.course.CourseReviewService;
import com.sep490.backend.learning.shared.ContentRequests;
import com.sep490.backend.learning.shared.ContentStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseReviewWorkflowIntegrationTests {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ContentStore db;
  @Autowired CourseContentService courses;
  @Autowired CourseReviewService workflow;

  long managerId, otherManagerId, adminId;
  LearnerPrincipal manager, otherManager, admin, learner;

  @BeforeEach
  void setup() {
    managerId=user("manager","CONTENT_MANAGER"); otherManagerId=user("other-manager","CONTENT_MANAGER"); adminId=user("review-admin","ADMIN");
    manager=new LearnerPrincipal(managerId,"manager@test","",true,"CONTENT_MANAGER");
    otherManager=new LearnerPrincipal(otherManagerId,"other@test","",true,"CONTENT_MANAGER");
    admin=new LearnerPrincipal(adminId,"admin@test","",true,"ADMIN");
    learner=new LearnerPrincipal(user("learner","LEARNER"),"learner@test","",true,"LEARNER");
  }

  @Test
  void managerOwnsDraftAndAdminAloneCanApproveIt() throws Exception {
    long course=createCourse(manager,"review-flow");
    assertThat(db.one("SELECT status,created_by FROM courses WHERE id=?",course))
        .containsEntry("status","DRAFT").containsEntry("createdBy",managerId);
    mvc.perform(get("/api/courses/"+course)).andExpect(status().isNotFound());
    mvc.perform(patch("/api/admin/courses/"+course+"/status").with(actor(admin)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REVIEW\"}"))
        .andExpect(status().isConflict());

    mvc.perform(get("/api/admin/courses/"+course).with(actor(otherManager)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/courses").with(actor(manager)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(course));
    mvc.perform(get("/api/admin/courses").with(actor(learner)))
        .andExpect(status().isForbidden());

    long module=json.readTree(mvc.perform(post("/api/admin/courses/"+course+"/modules")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"titleEn\":\"Module\",\"titleVi\":\"Học phần\",\"descriptionEn\":\"Description\"}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    long lesson=json.readTree(mvc.perform(post("/api/admin/modules/"+module+"/lessons")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"L1\",\"slug\":\"lesson-one\",\"titleEn\":\"Lesson one\",\"descriptionEn\":\"Description\",\"lessonType\":\"NORMAL\",\"estimatedMinutes\":10,\"prerequisiteIds\":[]}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(post("/api/admin/courses/"+course+"/submit").with(actor(manager)).with(csrf()))
        .andExpect(status().isBadRequest());
    finishCourse(course,lesson);

    mvc.perform(post("/api/admin/courses/"+course+"/submit").with(actor(manager)).with(csrf()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));
    mvc.perform(get("/api/courses/"+course)).andExpect(status().isNotFound());
    mvc.perform(post("/api/admin/courses/"+course+"/review").with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/admin/courses/"+course).with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson("review-flow")))
        .andExpect(status().isConflict());
    mvc.perform(patch("/api/admin/courses/"+course+"/status").with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/admin/courses/"+course+"/status").with(actor(admin)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isConflict());
    mvc.perform(put("/api/admin/courses/"+course).with(actor(admin)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson("review-flow")))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/admin/courses/"+course+"/review").with(actor(admin)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"note\":\"Nội dung đạt yêu cầu.\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.reviewNote").value("Nội dung đạt yêu cầu."));
    mvc.perform(get("/api/courses/"+course)).andExpect(status().isOk());
  }

  @Test
  void managerCanSeePublishedCatalogCoursesWithoutEditingThem() throws Exception {
    long ownDraft=courses.create(request("own-draft"),managerId);
    long published=courses.create(request("published-catalog"),adminId);
    db.update("UPDATE courses SET status='PUBLISHED' WHERE id=?",published);

    mvc.perform(get("/api/admin/courses").with(actor(manager)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == "+ownDraft+")]").exists())
        .andExpect(jsonPath("$.content[?(@.id == "+published+")]").exists());
    mvc.perform(get("/api/admin/courses/"+published).with(actor(manager)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(published));
    mvc.perform(put("/api/admin/courses/"+published).with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson("published-catalog")))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectionReturnsCourseForEditingAndDeleteArchivesIt() throws Exception {
    long course=courses.create(request("rejected-flow"),managerId);
    long module=courses.addModule(course,new ContentRequests.Module("Module","Description"));
    long lesson=courses.addLesson(module,new ContentRequests.Lesson("L2","lesson-two","Lesson two","Description","NORMAL",10,List.of()));
    finishCourse(course,lesson);
    workflow.submit(course,manager);
    workflow.review(course,"REJECT","Bổ sung mục tiêu học tập.",admin);
    assertThat(db.one("SELECT status,review_note FROM courses WHERE id=?",course))
        .containsEntry("status","REJECTED").containsEntry("reviewNote","Bổ sung mục tiêu học tập.");
    mvc.perform(get("/api/courses/"+course)).andExpect(status().isNotFound());

    mvc.perform(put("/api/admin/courses/"+course).with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson("rejected-flow")))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/admin/courses/"+course).with(actor(manager)).with(csrf()))
        .andExpect(status().isOk());
    assertThat(db.one("SELECT status FROM courses WHERE id=?",course)).containsEntry("status","ARCHIVED");
  }

  private long createCourse(LearnerPrincipal actor,String slug) throws Exception {
    return json.readTree(mvc.perform(post("/api/admin/courses").with(actor(actor)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson(slug)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
  }

  private void finishCourse(long course, long lesson) throws Exception {
    mvc.perform(post("/api/admin/lessons/"+lesson+"/activities")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"activityType\":\"TEXT\",\"titleEn\":\"Read\",\"contentJson\":{\"body\":\"Hello\"},\"isRequired\":true,\"maxScore\":0,\"status\":\"PUBLISHED\",\"grammarTopicIds\":[]}"))
        .andExpect(status().isOk());
    long question=json.readTree(mvc.perform(post("/api/admin/questions")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"questionType\":\"MULTIPLE_CHOICE\",\"promptEn\":\"Choose hello\",\"difficulty\":\"EASY\",\"status\":\"PUBLISHED\",\"options\":[{\"id\":\"a\",\"text\":\"Hello\"},{\"id\":\"b\",\"text\":\"Goodbye\"}],\"correctAnswerJson\":{\"optionIds\":[\"a\"]}}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    long lessonQuiz=quiz(course,lesson,"LESSON",question);
    mvc.perform(post("/api/admin/lessons/"+lesson+"/activities")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"activityType\":\"QUIZ\",\"titleEn\":\"Lesson quiz\",\"contentJson\":{},\"isRequired\":true,\"maxScore\":0,\"status\":\"PUBLISHED\",\"quizId\":"+lessonQuiz+",\"grammarTopicIds\":[]}"))
        .andExpect(status().isOk());
    mvc.perform(patch("/api/admin/lessons/"+lesson+"/status")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());
    long finalQuiz=quiz(course,lesson,"COURSE",question);
    mvc.perform(post("/api/admin/lessons/"+lesson+"/activities")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"activityType\":\"QUIZ\",\"titleEn\":\"Final quiz\",\"contentJson\":{},\"isRequired\":true,\"maxScore\":0,\"status\":\"PUBLISHED\",\"quizId\":"+finalQuiz+",\"grammarTopicIds\":[]}"))
        .andExpect(status().isOk());
    mvc.perform(patch("/api/admin/courses/"+course+"/final-assessment")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"quizId\":"+finalQuiz+"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/courses/"+course+"/readiness").with(actor(manager)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true));
  }

  private long quiz(long course, Long lesson, String type, long question) throws Exception {
    String body="{\"titleEn\":\""+type+" quiz\",\"quizType\":\""+type+"\",\"courseId\":"+course+
        ",\"lessonId\":"+(lesson==null?"null":lesson)+",\"passingScore\":70}";
    long quiz=json.readTree(mvc.perform(post("/api/admin/quizzes")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    mvc.perform(post("/api/admin/quizzes/"+quiz+"/questions")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"questionId\":"+question+",\"points\":1}"))
        .andExpect(status().isOk());
    mvc.perform(patch("/api/admin/quizzes/"+quiz+"/status")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());
    return quiz;
  }

  private String courseJson(String slug) throws Exception { return json.writeValueAsString(request(slug)); }
  private RequestPostProcessor actor(LearnerPrincipal principal) {
    return authentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  }
  private ContentRequests.Course request(String slug) {
    return new ContentRequests.Course("CODE-"+slug,slug,"Course "+slug,"Description","A1",null,60,"Outcome",true,"Khóa học","Mô tả");
  }
  private long user(String name, String role) {
    return db.insert("INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE',?)",
        name+UUID.randomUUID()+"@test",name+UUID.randomUUID(),"test",role);
  }
}
