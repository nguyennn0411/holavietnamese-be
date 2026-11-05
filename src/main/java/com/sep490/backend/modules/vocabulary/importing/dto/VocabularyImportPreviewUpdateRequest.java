package com.sep490.backend.modules.vocabulary.importing.dto;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Row;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Rows edited by the user after the Excel preview and before confirmation. */
public record VocabularyImportPreviewUpdateRequest(
        @NotNull @Size(min = 1, max = 20000) List<Row> rows) {}
