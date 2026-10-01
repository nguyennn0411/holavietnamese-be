package com.sep490.backend;


import com.sep490.backend.dto.courseimport.ImportSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

final class CourseImportWorkbookFixture {
    static byte[] workbook(Consumer<XSSFWorkbook> change) throws IOException {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            for (var spec : ImportSheet.values()) {
                var sheet = workbook.createSheet(spec.sheetName);
                var header = sheet.createRow(0);
                for (int c = 0; c < spec.columns.size(); c++) header.createCell(c).setCellValue(spec.columns.get(c));
            }
            add(workbook, ImportSheet.COURSE, "VI-A1,A1,Vietnamese A1,Tiếng Việt A1,Description,Mô tả,2.5,PUBLISHED");
            add(workbook, ImportSheet.UNITS, "VI-A1,A1-U1,Greetings,Chào hỏi,Introduction,Giới thiệu,1");
            add(workbook, ImportSheet.LESSONS, "A1-U1,A1-L1,Hello,Xin chào,Description,Mô tả,15,1,PUBLISHED");
            add(workbook, ImportSheet.BLOCKS, "A1-L1,A1-B1,INTRO,Welcome,Chào mừng,Hello world,Xin chào Việt Nam,1");
            add(workbook, ImportSheet.VOCABULARY, "A1-L1,chào,hello,lời chào,verb,Xin chào bạn,Hello friend,chào,,NORTH,EASY");
            add(workbook, ImportSheet.DIALOGUES, "A1-D1,A1-L1,Meeting,Gặp gỡ,First meeting,Lần đầu gặp");
            add(workbook, ImportSheet.DIALOGUE_LINES, "A1-D1,Lan,Xin chào!,Hello!,1,");
            add(workbook, ImportSheet.DRILLS, "A1-L1,Tôi là Lan,I am Lan,là,EASY,1,");
            add(workbook, ImportSheet.EXERCISES, "A1-E1,A1-L1,MULTIPLE_CHOICE,Chọn lời chào,Choose greeting,A,Chào là hello,Chào means hello,EASY,1");
            add(workbook, ImportSheet.OPTIONS, "A1-E1,A,Xin chào,Hello,true,1");
            add(workbook, ImportSheet.OPTIONS, "A1-E1,B,Tạm biệt,Goodbye,false,2");
            change.accept(workbook); workbook.write(bytes); return bytes.toByteArray();
        }
    }
    static void add(XSSFWorkbook workbook, ImportSheet spec, String csv) {
        var sheet = workbook.getSheet(spec.sheetName); var row = sheet.createRow(sheet.getLastRowNum()+1);
        var values = csv.split(",", -1);
        if (values.length != spec.columns.size()) throw new IllegalArgumentException("Fixture column count for " + spec);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }
    static void set(XSSFWorkbook workbook, ImportSheet spec, int row, String column, String value) {
        workbook.getSheet(spec.sheetName).getRow(row-1).getCell(spec.columns.indexOf(column)).setCellValue(value);
    }
}
