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

    @Test
    void regionalListFiltersByKeywordAndRegionButReturnsAllPublishedVariantsInGroup()
            throws Exception {
        jdbc.update(
                "INSERT INTO vocabularies (word, search_word, part_of_speech, status, created_at,"
                        + " updated_at) VALUES ('regional-filter-master', 'regional-filter-master',"
                        + " 'noun', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long master = jdbc.queryForObject(
                "SELECT id FROM vocabularies WHERE word='regional-filter-master'", Long.class);
        jdbc.update(
                "INSERT INTO regional_vocabularies (vocabulary_id, region, word, meaning, status,"
                        + " created_at, updated_at) VALUES (?, 'NORTH', 'regional-bắc', 'north match',"
                        + " 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", master);
        jdbc.update(
                "INSERT INTO regional_vocabularies (vocabulary_id, region, word, meaning, status,"
                        + " created_at, updated_at) VALUES (?, 'SOUTH', 'regional-nam', 'south match',"
                        + " 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", master);
        jdbc.update(
                "INSERT INTO regional_vocabularies (vocabulary_id, region, word, meaning, status,"
                        + " created_at, updated_at) VALUES (?, 'SOUTH', 'regional-draft', 'south match',"
                        + " 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", master);

        mvc.perform(get("/api/regional-vietnamese")
                        .param("keyword", "south match")
                        .param("region", "SOUTH")
                        .with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].vocabularyId").value(master))
                // Once a master matches, comparison returns all its published regional variants.
                .andExpect(jsonPath("$.result[0].variants.length()").value(2));
    }

    @Test
    void destinationListAppliesRegionFilter() throws Exception {
        jdbc.update(
                "INSERT INTO destinations (name_vi, name_en, slug, region, status, created_at,"
                        + " updated_at) VALUES ('Region North', 'Region North', 'region-north',"
                        + " 'NORTH', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update(
                "INSERT INTO destinations (name_vi, name_en, slug, region, status, created_at,"
                        + " updated_at) VALUES ('Region South', 'Region South', 'region-south',"
                        + " 'SOUTH', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        mvc.perform(get("/api/destinations")
                        .param("keyword", "Region")
                        .param("region", "SOUTH")
                        .with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].slug").value("region-south"));
    }

    @Test
    void destinationRegionalVocabularyUsesTenItemsPerBackendPage() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        jdbc.update(
                "INSERT INTO destinations (name_vi, name_en, slug, region, status, created_at,"
                    + " updated_at) VALUES (?, ?, ?, 'SOUTH', 'PUBLISHED', CURRENT_TIMESTAMP,"
                    + " CURRENT_TIMESTAMP)",
                "Phân trang " + suffix,
                "Paging " + suffix,
                "regional-page-" + suffix);
        Long destinationId =
                jdbc.queryForObject(
                        "SELECT id FROM destinations WHERE slug=?",
                        Long.class,
                        "regional-page-" + suffix);

        for (int index = 0; index < 11; index++) {
            String word = "regional-page-" + suffix + "-" + String.format("%02d", index);
            jdbc.update(
                    "INSERT INTO vocabularies (word, search_word, part_of_speech, status,"
                        + " created_at, updated_at) VALUES (?, ?, 'noun', 'PUBLISHED',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    word,
                    word);
            Long vocabularyId =
                    jdbc.queryForObject(
                            "SELECT id FROM vocabularies WHERE word=?", Long.class, word);
            jdbc.update(
                    "INSERT INTO destination_vocabularies (destination_id, vocabulary_id,"
                            + " display_order) VALUES (?, ?, ?)",
                    destinationId,
                    vocabularyId,
                    index);
            jdbc.update(
                    "INSERT INTO regional_vocabularies (vocabulary_id, region, word, meaning,"
                        + " status, created_at, updated_at) VALUES (?, 'SOUTH', ?, 'meaning',"
                        + " 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    vocabularyId,
                    "variant-" + index);
        }

        mvc.perform(
                        get("/api/destinations/" + destinationId + "/content")
                                .param("page", "0")
                                .param("size", "1")
                                .with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.regionalVariants.length()").value(10))
                .andExpect(jsonPath("$.result.regionalSize").value(10))
                .andExpect(jsonPath("$.result.regionalTotalElements").value(11))
                .andExpect(jsonPath("$.result.regionalTotalPages").value(2));

        mvc.perform(
                        get("/api/destinations/" + destinationId + "/content")
                                .param("page", "1")
                                .with(user(LEARNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.regionalVariants.length()").value(1))
                .andExpect(jsonPath("$.result.regionalPage").value(1));
    }
}
