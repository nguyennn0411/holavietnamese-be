package com.sep490.backend.dto.response;

import java.time.Instant;
import java.util.*;

public record ContentImportResponse(String id, String module, String filename, Long actorId, String actorName,
        String status, int totalRows, int validRows, int errorRows, int importedRows, int failedRows,
        boolean canConfirm, boolean success, Instant createdAt, Instant completedAt,
        List<Row> rows, List<Error> errors) {
    public record Row(int rowNumber, Map<String,String> values) {}
    public record Error(String sheet, int row, String column, String message) {}
    public record Type(String id, String label, String sheet, List<String> columns, List<String> requiredColumns) {}
}
