package com.sep490.backend.modules.admin.importing.service;

import com.sep490.backend.modules.admin.importing.dto.ContentImportResponse.*;
import com.sep490.backend.modules.admin.importing.dto.ContentImportResponse.Error;
import java.util.List;

/** A module supplies validation and atomic persistence; the framework owns uploads and history. */
public interface ContentImportHandler {
    Type type();
    List<Error> validate(List<Row> rows);
    void write(List<Row> rows);
    default String lockModule() { return type().id(); }
    default int importCount(List<Row> rows) { return rows.size(); }
}
