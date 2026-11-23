package com.sep490.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.account.seed.enabled=true")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class DefaultAccountsIntegrationTests {
    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ContentStore db;
    @Autowired PasswordEncoder passwords;

    @Test
    void seededAccountsAuthenticateWithTheirOwnRoles() throws Exception {
        String learner = token("learner", DEFAULT_PASSWORD);
        mvc.perform(get("/api/me/session").header("Authorization", learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LEARNER"));

        String content = token("content", DEFAULT_PASSWORD);
        mvc.perform(get("/api/me/session").header("Authorization", content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTENT"));
        mvc.perform(get("/api/content/courses").header("Authorization", content))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", content))
                .andExpect(status().isForbidden());

        String admin = token("admin", DEFAULT_PASSWORD);
        mvc.perform(get("/api/me/session").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admin/courses").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/content/courses").header("Authorization", admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAssignContentRole() throws Exception {
        String name = "new-manager-" + UUID.randomUUID();
        long id = db.insert(
                "INSERT INTO users(email,username,password_hash,enabled,status,role) "
                        + "VALUES(?,?,?,true,'ACTIVE','LEARNER')",
                name + "@example.com", name, passwords.encode("Test-pass123"));

        mvc.perform(put("/api/admin/users/" + id + "/role")
                        .header("Authorization", token("admin", DEFAULT_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT\"}"))
                .andExpect(status().isOk());

        String content = token(name, "Test-pass123");
        mvc.perform(get("/api/me/session").header("Authorization", content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTENT"));
    }

    private String token(String username, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        String response = mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("result").path("token").asText();
    }
}
