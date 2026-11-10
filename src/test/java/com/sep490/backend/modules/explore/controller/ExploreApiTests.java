package com.sep490.backend.modules.explore.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;

class ExploreApiTests extends ContentApiTestSupport {
    @Test
    void regionalComparisonRequiresPublishedMasterAndVariant() throws Exception {
        jdbc.update(
                "INSERT INTO vocabularies (word, search_word, part_of_speech, status, created_at,"
                    + " updated_at) VALUES ('refactor-dua', 'refactor-dua', 'noun', 'PUBLISHED',"
                    + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long master =
                jdbc.queryForObject(
                        "SELECT id FROM vocabularies WHERE word='refactor-dua'", Long.class);
        jdbc.update(
                "INSERT INTO regional_vocabularies (vocabulary_id, region, word, meaning, status,"
                        + " created_at, updated_at) VALUES (?, 'SOUTH', 'refactor-thom', 'qua dua',"
                        + " 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                master);
        mvc.perform(get("/api/regional-vietnamese/" + master).with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.variants[0].word").value("refactor-thom"));
        jdbc.update("UPDATE vocabularies SET status='DRAFT' WHERE id=?", master);
        mvc.perform(get("/api/regional-vietnamese/" + master).with(user(LEARNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void destinationListFiltersDraftsAndPreservesContentRoute() throws Exception {
        jdbc.update(
                "INSERT INTO destinations (name_vi, name_en, slug, region, status, created_at,"
                    + " updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Refactor public",
                "Refactor public",
                "refactor-public",
                "NORTH",
                "PUBLISHED");
        jdbc.update(
                "INSERT INTO destinations (name_vi, name_en, slug, region, status, created_at,"
                    + " updated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Refactor hidden",
                "Refactor hidden",
                "refactor-hidden",
                "NORTH",
                "DRAFT");
        mvc.perform(get("/api/destinations").param("keyword", "Refactor").with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
        Long hidden =
                jdbc.queryForObject(
                        "SELECT id FROM destinations WHERE slug='refactor-hidden'", Long.class);
        mvc.perform(get("/api/destinations/" + hidden + "/content").with(user(LEARNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void learnerCannotManageRegionalVocabularyOrDestinations() throws Exception {
        mvc.perform(get("/api/admin/destinations").with(user(LEARNER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/regional-language").with(user(LEARNER)))
                .andExpect(status().isForbidden());
    }
}
