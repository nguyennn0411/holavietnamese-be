package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.ContentImportService;
import java.io.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/imports") @PreAuthorize("hasRole('ADMIN')")
public class ContentImportController {
    private final ContentImportService service;
    @GetMapping("/types") public ApiResponse<List<ContentImportResponse.Type>> types() { return ApiResponse.success(service.types()); }
    @PostMapping(value = "/preview",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ContentImportResponse> preview(@RequestParam String module,@RequestParam MultipartFile file,
            @AuthenticationPrincipal LearnerPrincipal actor) throws IOException {
        return ApiResponse.success(service.preview(module,file.getBytes(),file.getOriginalFilename(),actor));
    }
    @PostMapping("/{id}/confirm") public ApiResponse<ContentImportResponse> confirm(@PathVariable String id,@AuthenticationPrincipal LearnerPrincipal actor) {
        return ApiResponse.success(service.confirm(id,actor));
    }
    @GetMapping public ApiResponse<Page<ContentImportResponse>> history(@RequestParam(defaultValue = "0") int page,@RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.history(page,size));
    }
    @GetMapping("/{id}") public ApiResponse<ContentImportResponse> detail(@PathVariable String id) { return ApiResponse.success(service.detail(id)); }
    @GetMapping("/template") public ResponseEntity<byte[]> template(@RequestParam String module) throws IOException {
        var type = service.type(module);
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(type.sheet()); var header = sheet.createRow(0);
            for (int i=0;i<type.columns().size();i++) { header.createCell(i).setCellValue(type.columns().get(i)); sheet.setColumnWidth(i,24*256); }
            sheet.createFreezePane(0,1); workbook.write(out);
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+module.toLowerCase(java.util.Locale.ROOT)+"-template.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(out.toByteArray());
        }
    }
}
