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
    managerId=user("manager"); otherManagerId=user("other-manager"); adminId=user("review-admin");
    manager=new LearnerPrincipal(managerId,"manager@test","",true,"CONTENT_MANAGER");
    otherManager=new LearnerPrincipal(otherManagerId,"other@test","",true,"CONTENT_MANAGER");
    admin=new LearnerPrincipal(adminId,"admin@test","",true,"ADMIN");
    learner=new LearnerPrincipal(user("learner"),"learner@test","",true,"LEARNER");
  }

  @Test
  void managerOwnsDraftAndAdminAloneCanApproveIt() throws Exception {
    long course=createCourse(manager,"review-flow");
    assertThat(db.one("SELECT status,created_by FROM courses WHERE id=?",course))
        .containsEntry("status","DRAFT").containsEntry("createdBy",managerId);

    mvc.perform(get("/api/admin/courses/"+course).with(actor(otherManager)))
        .andExpect(status().isForbidden());
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
    mvc.perform(post("/api/admin/lessons/"+lesson+"/activities")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"activityType\":\"TEXT\",\"titleEn\":\"Read\",\"contentJson\":{\"textEn\":\"Hello\"},\"isRequired\":true,\"maxScore\":0,\"status\":\"PUBLISHED\",\"grammarTopicIds\":[]}"))
        .andExpect(status().isOk());
    mvc.perform(patch("/api/admin/lessons/"+lesson+"/status")
        .with(actor(manager)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isOk());

    mvc.perform(post("/api/admin/courses/"+course+"/submit").with(actor(manager)).with(csrf()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));
    mvc.perform(put("/api/admin/courses/"+course).with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(courseJson("review-flow")))
        .andExpect(status().isConflict());
    mvc.perform(patch("/api/admin/courses/"+course+"/status").with(actor(manager)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
        .andExpect(status().isForbidden());

    mvc.perform(post("/api/admin/courses/"+course+"/review").with(actor(admin)).with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"note\":\"Nội dung đạt yêu cầu.\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.reviewNote").value("Nội dung đạt yêu cầu."));
    mvc.perform(get("/api/courses/"+course)).andExpect(status().isOk());
  }

  @Test
  void rejectionReturnsCourseForEditingAndDeleteArchivesIt() throws Exception {
    long course=courses.create(request("rejected-flow"),managerId);
    long module=courses.addModule(course,new ContentRequests.Module("Module","Description"));
    courses.addLesson(module,new ContentRequests.Lesson("L2","lesson-two","Lesson two","Description","NORMAL",10,List.of()));
    workflow.submit(course,manager);
    workflow.review(course,"REJECT","Bổ sung mục tiêu học tập.",admin);
    assertThat(db.one("SELECT status,review_note FROM courses WHERE id=?",course))
        .containsEntry("status","REJECTED").containsEntry("reviewNote","Bổ sung mục tiêu học tập.");

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

  private String courseJson(String slug) throws Exception { return json.writeValueAsString(request(slug)); }
  private RequestPostProcessor actor(LearnerPrincipal principal) {
    return authentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
  }
  private ContentRequests.Course request(String slug) {
    return new ContentRequests.Course("CODE-"+slug,slug,"Course "+slug,"Description","A1",null,60,"Outcome",true,"Khóa học","Mô tả");
  }
  private long user(String name) {
    return db.insert("INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','LEARNER')",
        name+UUID.randomUUID()+"@test",name+UUID.randomUUID(),"test");
  }
}
