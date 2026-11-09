package com.sep490.backend.modules.vocabulary.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Row;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportHandler;
import com.sep490.backend.support.ContentApiTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

class VocabularyImportFlowTests extends ContentApiTestSupport {
    @Autowired VocabularyImportHandler handler;

    @Test
    void importsDraftWordAndRegionalVariantAndSkipsDuplicatesOnRetry() {
        var rows =
                List.of(
                        new Row(
                                2,
                                Map.of(
                                        "word",
                                        "refactor-import-dua",
                                        "part_of_speech",
                                        "noun",
                                        "definition_vi",
                                        "Qua dua",
                                        "region",
                                        "SOUTH",
                                        "regional_word",
                                        "refactor-import-thom",
                                        "regional_meaning",
                                        "Qua dua")));
        assertThat(handler.validate(rows)).isEmpty();
        // Ket qua dem ca tu chuan va bien the vung mien duoc tao.
        assertThat(handler.write(rows).importedRows()).isEqualTo(2);
        assertThat(handler.write(rows).importedRows()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM vocabularies WHERE word='refactor-import-dua'"
                                        + " AND status='DRAFT'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM regional_vocabularies WHERE"
                                        + " word='refactor-import-thom' AND status='DRAFT'",
                                Integer.class))
                .isEqualTo(1);
    }
}
