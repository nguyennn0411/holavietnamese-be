package com.sep490.backend.modules.vocabulary.importing.service;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import java.util.List;

/** A module supplies validation and atomic persistence; the framework owns uploads and history. */
public interface VocabularyImportHandlerContract {
    Type type();
    List<Error> validate(List<Row> rows);
    void write(List<Row> rows);
    default String lockModule() { return type().id(); }
    default int importCount(List<Row> rows) { return rows.size(); }
}
