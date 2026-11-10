package com.sep490.backend.modules.vocabulary.importing.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportPreviewUpdateRequest;
import com.sep490.backend.modules.vocabulary.importing.dto.VocabularyImportResponse;
import com.sep490.backend.modules.vocabulary.importing.service.VocabularyImportService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/imports")
@PreAuthorize(ContentPermissions.VOCABULARY_IMPORT_ACCESS)
public class VocabularyImportController {
    private final VocabularyImportService service;

    /** Tra loai du lieu va cau truc cot Excel duoc ho tro. */
    @GetMapping("/types")
    public ApiResponse<List<VocabularyImportResponse.Type>> types() {
        return ApiResponse.success(service.types());
    }

    /** Doc va kiem tra file, luu ban xem truoc truoc khi xac nhan import. */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<VocabularyImportResponse> preview(
            @RequestParam String module,
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal LearnerPrincipal actor)
            throws IOException {
        try (var input = file.getInputStream()) {
            return ApiResponse.success(
                    service.preview(module, input, file.getOriginalFilename(), actor));
        }
    }

    /** Kiem tra lai ban xem truoc va luu du lieu mot lan cho moi phien import. */
    @PostMapping("/{id}/confirm")
    public ApiResponse<VocabularyImportResponse> confirm(
            @PathVariable String id, @AuthenticationPrincipal LearnerPrincipal actor) {
        return ApiResponse.success(service.confirm(id, actor));
    }

    /** Kiem tra va luu cac dong nguoi dung sua trong ban xem truoc. */
    @PutMapping("/{id}/preview")
    public ApiResponse<VocabularyImportResponse> updatePreview(
            @PathVariable String id,
            @Valid @RequestBody VocabularyImportPreviewUpdateRequest input,
            @AuthenticationPrincipal LearnerPrincipal actor) {
        return ApiResponse.success(service.updatePreview(id, input.rows(), actor));
    }

    /** Tra lich su import theo phan trang. */
    @GetMapping
    public ApiResponse<Page<VocabularyImportResponse>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.history(page, size));
    }

    /** Lay chi tiet phien import va ket qua xu ly tung dong. */
    @GetMapping("/{id}")
    public ApiResponse<VocabularyImportResponse> detail(@PathVariable String id) {
        return ApiResponse.success(service.detail(id));
    }

    /** Tao file Excel mau theo dung cau truc cot dang duoc ho tro. */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam String module) throws IOException {
        var type = service.type(module);
        try (var workbook = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(type.sheet());
            var header = sheet.createRow(0);
            for (int i = 0; i < type.columns().size(); i++) {
                header.createCell(i).setCellValue(type.columns().get(i));
                sheet.setColumnWidth(i, 24 * 256);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\""
                                    + module.toLowerCase(java.util.Locale.ROOT)
                                    + "-template.xlsx\"")
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }
}
