package com.sep490.backend.modules.vocabulary.importing.service;

import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.*;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse.Error;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.zip.ZipInputStream;

@Component
public class VocabularyExcelReader {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public record WorkbookData(List<Row> rows, List<Error> errors) {}
    public WorkbookData read(byte[] bytes, String filename, Type type) {
        var rows = new ArrayList<Row>(); var errors = new ArrayList<Error>();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || bytes.length == 0 || bytes.length > MAX_BYTES) {
            return new WorkbookData(List.of(),List.of(new Error(type.sheet(),0,"file","Chọn file .xlsx không rỗng, tối đa 10 MiB.")));
        }
        try {
            checkArchive(bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheet(type.sheet());
                if (sheet == null) return new WorkbookData(List.of(),List.of(new Error(type.sheet(),1,"sheet","Thiếu sheet " + type.sheet())));
                if (sheet.getLastRowNum() > 20000) throw new IOException("Row limit");
                var headers = new LinkedHashMap<String,Integer>();
                var header = sheet.getRow(0);
                if (header != null) {
                    if (header.getLastCellNum() > 100) throw new IOException("Column limit");
                    for (Cell cell : header) {
                        String name = value(cell);
                        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
                            errors.add(new Error(type.sheet(),1,"header","Header không được chứa công thức hoặc lỗi Excel."));
                        if (name.isEmpty()) continue;
                        if (!type.columns().contains(name)) errors.add(new Error(type.sheet(),1,name,"Cột không được hỗ trợ."));
                        if (headers.putIfAbsent(name,cell.getColumnIndex()) != null) errors.add(new Error(type.sheet(),1,name,"Header bị trùng."));
                    }
                }
                for (String column : type.requiredColumns()) if (!headers.containsKey(column)) errors.add(new Error(type.sheet(),1,column,"Thiếu cột bắt buộc."));
                for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                    var source = sheet.getRow(index); if (source == null) continue;
                    var fields = new LinkedHashMap<String,String>(); boolean invalidCell = false;
                    for (String name : type.columns()) {
                        Cell cell = headers.containsKey(name) ? source.getCell(headers.get(name)) : null;
                        if (cell != null && (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)) {
                            errors.add(new Error(type.sheet(),index+1,name,"Không nhận công thức hoặc ô lỗi; hãy dán giá trị.")); invalidCell = true;
                        }
                        fields.put(name,value(cell));
                    }
                    if (invalidCell || fields.values().stream().anyMatch(s -> !s.isBlank())) rows.add(new Row(index+1,fields));
                }
                if (rows.isEmpty()) errors.add(new Error(type.sheet(),0,"file","Workbook chưa có dòng dữ liệu."));
            }
        } catch (IOException | RuntimeException ex) {
            errors.add(new Error(type.sheet(),0,"file","Không đọc được XLSX, file mã hóa hoặc vượt giới hạn Excel hiện có (50 MiB giải nén, 2.000 ZIP entry, 20.000 dòng)."));
        }
        return new WorkbookData(List.copyOf(rows),List.copyOf(errors));
    }
    private static String value(Cell cell) {
        if (cell == null) return "";
        return switch(cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }
    private static void checkArchive(byte[] bytes) throws IOException {
        int entries = 0; long expanded = 0; byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            while (zip.getNextEntry() != null) {
                if (++entries > 2000) throw new IOException("ZIP entry limit");
                int n; while ((n = zip.read(buffer)) != -1) if ((expanded += n) > 50L*1024*1024) throw new IOException("Expanded limit");
            }
        }
        if (entries == 0) throw new IOException("Not ZIP");
    }
}
