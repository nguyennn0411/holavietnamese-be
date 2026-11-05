package com.sep490.backend.modules.vocabulary.importing.service;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import java.util.Map;
import java.util.List;

/** A module supplies validation and atomic persistence; the framework owns uploads and history. */
public interface VocabularyImportHandlerContract {
    record WriteResult(int importedRows, Map<String,Object> summary) {
        public WriteResult {
            summary = summary == null ? Map.of() : Map.copyOf(summary);
        }
    }

    Type type();
    List<Error> validate(List<Row> rows);
    default Map<String,Object> previewSummary(List<Row> rows) { return Map.of(); }
    WriteResult write(List<Row> rows);
    default String lockModule() { return type().id(); }
}
