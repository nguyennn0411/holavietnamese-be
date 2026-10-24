package com.sep490.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.learning.shared.ContentStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "APP_DEMO_PASSWORD=LongTestPass123")
@AutoConfigureMockMvc
@ActiveProfiles({"mysql-test", "dev-demo"})
class DevDemoAccountsIntegrationTests {
  private static final String DEMO_PASSWORD = "LongTestPass123";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ContentStore db;
  @Autowired PasswordEncoder passwords;

  @Test
  void seededAccountsAuthenticateWithTheirOwnRoles() throws Exception {
    String manager = token("contentmanager", DEMO_PASSWORD);
    mvc.perform(get("/api/me/session").header("Authorization", manager))
        .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CONTENT_MANAGER"));
    mvc.perform(get("/api/admin/courses").header("Authorization", manager))
        .andExpect(status().isOk());
    mvc.perform(get("/api/admin/users").header("Authorization", manager))
        .andExpect(status().isForbidden());

    String admin = token("admin", DEMO_PASSWORD);
    mvc.perform(get("/api/me/session").header("Authorization", admin))
        .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
    mvc.perform(get("/api/admin/courses").header("Authorization", admin))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanAssignContentManagerRole() throws Exception {
    String name = "new-manager-" + UUID.randomUUID();
    long id = db.insert("INSERT INTO users(email,username,password_hash,enabled,status,role) VALUES(?,?,?,true,'ACTIVE','LEARNER')",
        name + "@test.local", name, passwords.encode("Test-pass123"));
    mvc.perform(put("/api/admin/users/" + id + "/roles")
        .header("Authorization", token("admin", DEMO_PASSWORD))
        .contentType(MediaType.APPLICATION_JSON).content("[\"CONTENT_MANAGER\"]"))
        .andExpect(status().isOk());
    String manager = token(name, "Test-pass123");
    mvc.perform(get("/api/me/session").header("Authorization", manager))
        .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CONTENT_MANAGER"));
    mvc.perform(get("/api/admin/courses").header("Authorization", manager))
        .andExpect(status().isOk());
  }

  private String token(String username, String password) throws Exception {
    String body = json.writeValueAsString(Map.of("username", username, "password", password));
    String response = mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
        .content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return "Bearer " + json.readTree(response).path("result").path("token").asText();
  }
}
