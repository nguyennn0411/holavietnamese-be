package com.sep490.backend;

import com.sep490.backend.common.security.LearnerPrincipal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VocabularyBulkReviewIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final LearnerPrincipal reviewer = new LearnerPrincipal(
            840001L, "vocabulary-reviewer@example.test", "", true, "ADMIN");
    private final LearnerPrincipal content = new LearnerPrincipal(
            840002L, "vocabulary-content@example.test", "", true, "CONTENT", Set.of("VOCABULARY_WRITE"));

    @BeforeEach
    void fixture() {
        insert("chờ duyệt một", "PENDING_REVIEW");
        insert("chờ duyệt hai", "PENDING_REVIEW");
        insert("bản nháp giữ nguyên", "DRAFT");
        insert("đã xuất bản giữ nguyên", "PUBLISHED");
    }

    @Test
    void reviewerApprovesEveryPendingWordAndLeavesOtherStatusesUntouched() throws Exception {
        mvc.perform(post("/api/admin/vocabulary/review/approve-all").with(user(content)).with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/vocabulary/review/approve-all").with(user(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(2));

        assertThat(count("PENDING_REVIEW")).isZero();
        assertThat(count("PUBLISHED")).isEqualTo(3);
        assertThat(count("DRAFT")).isEqualTo(1);

        mvc.perform(post("/api/admin/vocabulary/review/approve-all").with(user(reviewer)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0));
    }

    private void insert(String word, String status) {
        jdbc.update("INSERT INTO vocabularies(word,part_of_speech,cefr_level,status) VALUES(?, 'noun', 'A1', ?)",
                word, status);
    }

    private int count(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE status=?", Integer.class, status);
    }
}
