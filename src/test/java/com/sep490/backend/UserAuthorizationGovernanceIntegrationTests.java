package com.sep490.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization boundary tests for the one-role account model. The ADMIN
 * fixture is inserted as a persisted record here (not created through an API),
 * mirroring production provisioning.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAuthorizationGovernanceIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User adminUser;
    private User learnerUser;
    private String adminToken;
    private String learnerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = persistedUser("admin", "ADMIN", "AdminPass123");
        learnerUser = persistedUser("learner", "LEARNER", "LearnerPass123");
        adminToken = obtainToken(adminUser.getUsername(), "AdminPass123");
        learnerToken = obtainToken(learnerUser.getUsername(), "LearnerPass123");
    }

    @Test
    void publicRegistrationAlwaysCreatesLearnerAndNeverReturnsRolesArray() throws Exception {
        String username = unique("registration");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@test.local",
                                "password", "SecurePass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("LEARNER"))
                .andExpect(jsonPath("$.result.roles").doesNotExist());

        User created = userRepository.findByUsername(username).orElseThrow();
        assertThat(created.getRole()).isEqualTo("LEARNER");
    }

    @Test
    void registrationAndProfilePayloadsCannotSmuggleRole() throws Exception {
        String username = unique("blocked-registration");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@test.local",
                                "password", "SecurePass123",
                                "role", "ADMIN"))))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername(username)).isEmpty();

        mvc.perform(put("/api/users/me")
                        .header("Authorization", learnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"No escalation\",\"role\":\"CONTENT\"}"))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findById(learnerUser.getId()).orElseThrow().getRole()).isEqualTo("LEARNER");
    }

    @Test
    void nonAdminCannotChangeOwnOrAnotherUsersRole() throws Exception {
        mvc.perform(put("/api/admin/users/" + learnerUser.getId() + "/role")
                        .header("Authorization", learnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT\"}"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findById(learnerUser.getId()).orElseThrow().getRole()).isEqualTo("LEARNER");
    }

    @Test
    void adminCanOnlySwitchAnotherAccountBetweenLearnerAndContent() throws Exception {
        mvc.perform(put("/api/admin/users/" + learnerUser.getId() + "/role")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("CONTENT"))
                .andExpect(jsonPath("$.result.roles").doesNotExist());
        assertThat(userRepository.findById(learnerUser.getId()).orElseThrow().getRole()).isEqualTo("CONTENT");

        mvc.perform(put("/api/admin/users/" + learnerUser.getId() + "/role")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LEARNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("LEARNER"));
        assertThat(userRepository.findById(learnerUser.getId()).orElseThrow().getRole()).isEqualTo("LEARNER");
    }

    @Test
    void apiRejectsAdminTargetAdminAssignmentManyRolesAndInvalidRole() throws Exception {
        String url = "/api/admin/users/" + learnerUser.getId() + "/role";

        mvc.perform(put(url).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/admin/users/" + adminUser.getId() + "/role")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LEARNER\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(url).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"LEARNER\",\"CONTENT\"]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(url).header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(learnerUser.getId()).orElseThrow().getRole()).isEqualTo("LEARNER");
        assertThat(userRepository.findById(adminUser.getId()).orElseThrow().getRole()).isEqualTo("ADMIN");
    }

    @Test
    void loginAndPriorTokenFollowCurrentSingleRole() throws Exception {
        mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", learnerUser.getUsername(),
                                "password", "LearnerPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("LEARNER"))
                .andExpect(jsonPath("$.result.roles").doesNotExist());

        mvc.perform(get("/api/me/session").header("Authorization", learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LEARNER"));

        Thread.sleep(25);
        mvc.perform(put("/api/admin/users/" + learnerUser.getId() + "/role")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me/session").header("Authorization", learnerToken))
                .andExpect(status().isUnauthorized());

        String refreshedToken = obtainToken(learnerUser.getUsername(), "LearnerPass123");
        mvc.perform(get("/api/me/session").header("Authorization", refreshedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONTENT"));
    }

    private User persistedUser(String prefix, String role, String password) {
        String username = unique(prefix);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.local");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    private String obtainToken(String username, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("username", username, "password", password));
        String response = mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).path("result").path("token").asText();
    }

    private String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
