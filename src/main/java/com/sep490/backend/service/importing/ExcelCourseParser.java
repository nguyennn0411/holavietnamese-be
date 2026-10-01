package com.sep490.backend.service.importing;


import com.sep490.backend.dto.courseimport.*;
import com.sep490.backend.service.importing.ExcelCourseParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class ExcelCourseParser {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ROWS = 20000;
    public CourseImportCommand parse(byte[] bytes, String filename) {
        Map<ImportSheet, List<CourseImportCommand.Row>> data = new EnumMap<>(ImportSheet.class);
        List<CourseImportError> errors = new ArrayList<>(), warnings = new ArrayList<>();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || bytes.length == 0 || bytes.length > MAX_BYTES) {
            errors.add(new CourseImportError("", 0, "file", "", "Upload a non-empty .xlsx file no larger than 10 MiB."));
            return new CourseImportCommand(data, errors, warnings);
        }
        try {
            checkArchive(bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                int total = 0;
                for (ImportSheet spec : ImportSheet.values()) {
                    var sheet = workbook.getSheet(spec.sheetName);
                    List<CourseImportCommand.Row> rows = new ArrayList<>(); data.put(spec, rows);
                    if (sheet == null) { errors.add(new CourseImportError(spec.sheetName, 1, "", "", "Required sheet is missing.")); continue; }
                    if (sheet.getLastRowNum() > MAX_ROWS) {
                        errors.add(new CourseImportError(spec.sheetName, 0, "", "", "Sheet exceeds 20,000 data rows.")); continue;
                    }
                    Map<String, Integer> headers = new HashMap<>();
                    var header = sheet.getRow(0);
                    if (header != null) {
                        if (header.getLastCellNum() > 100) throw new IOException("Too many columns");
                        for (Cell cell : header) {
                            String name = value(cell);
                            if (name.isEmpty()) continue;
                            if (headers.putIfAbsent(name, cell.getColumnIndex()) != null)
                                errors.add(new CourseImportError(spec.sheetName, 1, name, name, "Duplicate column."));
                            if (!spec.columns.contains(name)) warnings.add(new CourseImportError(spec.sheetName, 1, name, name, "Unknown column is ignored."));
                        }
                    }
                    for (String column : spec.columns) if (!headers.containsKey(column))
                        errors.add(new CourseImportError(spec.sheetName, 1, column, "", "Required column is missing."));
                    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                        var source = sheet.getRow(i); if (source == null) continue;
                        Map<String, String> values = new LinkedHashMap<>();
                        for (String column : spec.columns) {
                            var cell = headers.containsKey(column) ? source.getCell(headers.get(column)) : null;
                            if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR))
                                errors.add(new CourseImportError(spec.sheetName, i + 1, column, "", "Formula and error cells are not accepted; paste values instead."));
                            values.put(column, value(cell));
                        }
                        if (values.values().stream().allMatch(String::isBlank)) continue;
                        if (++total > 50000) throw new IOException("Too many rows");
                        rows.add(new CourseImportCommand.Row(spec, i + 1, values));
                    }
                }
                Set<String> names = new HashSet<>();
                for (var spec : ImportSheet.values()) names.add(spec.sheetName);
                for (var sheet : workbook) if (!names.contains(sheet.getSheetName()))
                    warnings.add(new CourseImportError(sheet.getSheetName(), 0, "", "", "Unknown sheet is ignored."));
            }
        } catch (IOException | RuntimeException ex) {
            errors.add(new CourseImportError("", 0, "file", "", "Invalid, encrypted, or oversized XLSX archive. Limit: 50 MiB uncompressed / 50,000 total rows."));
        }
        return new CourseImportCommand(data, errors, warnings);
    }
    private static String value(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }
    // Check expanded size before POI constructs its in-memory workbook; never relax POI zip-bomb protection.
    private static void checkArchive(byte[] bytes) throws IOException {
        long expanded = 0; int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                if (++entries > 2000) throw new IOException("Too many archive entries");
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 50L * 1024 * 1024) throw new IOException("Expanded archive too large");
                }
            }
        }
        if (entries == 0) throw new IOException("Not an XLSX archive");
    }
}
