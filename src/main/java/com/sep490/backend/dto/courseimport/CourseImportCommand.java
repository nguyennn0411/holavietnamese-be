package com.sep490.backend.dto.courseimport;

import java.util.*;

public record CourseImportCommand(Map<ImportSheet, List<Row>> sheets,
                                  List<CourseImportError> errors, List<CourseImportError> warnings) {
    public CourseImportCommand {
        var copy = new EnumMap<ImportSheet, List<Row>>(ImportSheet.class);
        sheets.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        sheets = Collections.unmodifiableMap(copy);
        errors = List.copyOf(errors); warnings = List.copyOf(warnings);
    }
    public record Row(ImportSheet sheet, int number, Map<String, String> values) {
        public Row { values = Map.copyOf(values); }
        public String get(String column) { return values.getOrDefault(column, ""); }
        public String key() {
            return sheet.scopedKey() ? get(sheet.reference) + "\u001f" + get(sheet.key) : get(sheet.key);
        }
        public CourseImportError error(String column, String message) {
            return new CourseImportError(sheet.sheetName, number, column, get(column), message);
        }
    }
    public List<Row> rows(ImportSheet sheet) { return sheets.getOrDefault(sheet, List.of()); }
    public String courseCode() { return rows(ImportSheet.COURSE).isEmpty() ? null : rows(ImportSheet.COURSE).getFirst().get("code"); }
    public Map<String, Integer> statistics() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (var sheet : ImportSheet.values()) result.put(sheet.statistic, rows(sheet).size());
        return result;
    }
}
