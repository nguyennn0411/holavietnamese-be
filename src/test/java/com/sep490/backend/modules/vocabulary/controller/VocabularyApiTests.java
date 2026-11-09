package com.sep490.backend.modules.vocabulary.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;

class VocabularyApiTests extends ContentApiTestSupport {
    @Test
    void dictionaryShowsPublishedWordsAndPreservesPagination() throws Exception {
        jdbc.update(
                "INSERT INTO vocabularies (word, search_word, part_of_speech, status, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "refactor-public",
                "refactor-public",
                "noun",
                "PUBLISHED");
        jdbc.update(
                "INSERT INTO vocabularies (word, search_word, part_of_speech, status, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "refactor-hidden",
                "refactor-hidden",
                "noun",
                "DRAFT");
        mvc.perform(get("/api/vocabulary").param("keyword", "refactor-").with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].word").value("refactor-public"));
        mvc.perform(get("/api/vocabulary/search").param("keyword", "refactor-").with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void learnerCannotReadAnotherNotebookEntryOrAdminCatalog() throws Exception {
        mvc.perform(get("/api/me/vocabulary/999999").with(user(LEARNER)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/vocabulary").with(user(LEARNER)))
                .andExpect(status().isForbidden());
    }
}
