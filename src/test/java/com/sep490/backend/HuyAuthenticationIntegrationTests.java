package com.sep490.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.learning.shared.ContentStore;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HuyAuthenticationIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ContentStore db;
    @Autowired PasswordEncoder passwords;
    @Autowired jakarta.persistence.EntityManager entities;

    @Test void registrationTokenConnectsProfileCourseAndOverview() throws Exception {
        String username = "learner" + UUID.randomUUID().toString().replace("-", "");
        var response = mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", username, "email", username+"@test.local", "password", "Test-pass123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var result=json.readTree(response).path("result");
        long id=result.path("id").asLong();
        String bearer="Bearer "+result.path("token").asText();
        mvc.perform(get("/api/me/session").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/users/me").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value(id));
        mvc.perform(put("/api/users/settings").header("Authorization",bearer)
            .contentType("application/json").content("{\"dailyLearningGoalMinutes\":25}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value(id));
        mvc.perform(get("/api/users/progress/summary").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.dailyGoalMinutes").value(25));
        mvc.perform(get("/api/users/notifications").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/users/achievements").header("Authorization",bearer)).andExpect(status().isOk());
        long course=db.insert("INSERT INTO courses(title,description,level,estimated_duration,status) VALUES('JWT Course','Test','A1',10,'PUBLISHED')");
        mvc.perform(post("/api/courses/"+course+"/enroll").header("Authorization",bearer))
            .andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/users/progress").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.enrolledCourses[0].id").value(course))
            .andExpect(jsonPath("$.result.quizHistory").isEmpty());
        mvc.perform(get("/api/admin/courses").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/me/courses").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }

    @Test void legacyAccountLoginKeepsOriginalIdAndAdminRights() throws Exception {
        String email=UUID.randomUUID()+"@test.local";
        long id=db.insert("INSERT INTO users(email,password_hash,enabled,role) VALUES(?,?,true,'ADMIN')",email,passwords.encode("Test-pass123"));
        String bearer=login(email);
        mvc.perform(get("/api/me/session").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admin/courses").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users/"+id).header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.id").value(id));
        db.update("UPDATE users SET enabled=false WHERE id=?",id);
        entities.clear();
        mvc.perform(get("/api/me/courses").header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }

    @Test void overviewDoesNotExposeOtherLearnersRecords() throws Exception {
        String email=UUID.randomUUID()+"@test.local";
        db.insert("INSERT INTO users(email,password_hash,enabled) VALUES(?,?,true)",email,passwords.encode("Test-pass123"));
        mvc.perform(get("/api/users/progress").header("Authorization",login(email)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.enrolledCourses").isEmpty())
            .andExpect(jsonPath("$.result.completedLessons").value(0));
        mvc.perform(get("/api/users/progress")).andExpect(status().isUnauthorized());
    }

    private String login(String email) throws Exception {
        var response=mvc.perform(post("/api/auth/token").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",email,"password","Test-pass123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(response).path("result").path("token").asText();
    }
}
