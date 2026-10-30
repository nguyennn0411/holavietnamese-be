package com.sep490.backend.modules.culture.controller.admin;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.culture.dto.request.CultureArticleRequest;
import com.sep490.backend.modules.culture.dto.request.CultureCategoryRequest;
import com.sep490.backend.modules.culture.dto.response.CultureArticleResponse;
import com.sep490.backend.modules.culture.dto.response.CultureCategoryResponse;
import com.sep490.backend.modules.culture.service.AdminCultureService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/culture")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'CONTENT_MANAGER')")
public class AdminCultureController {

    AdminCultureService adminCultureService;

    // --- ARTICLES ---

    @GetMapping("/articles")
    public ApiResponse<List<CultureArticleResponse>> getAllArticles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "all") String status
    ) {
        return ApiResponse.<List<CultureArticleResponse>>builder()
                .result(adminCultureService.getAllArticles(search, category, status))
                .build();
    }

    @GetMapping("/articles/{id}")
    public ApiResponse<CultureArticleResponse> getArticle(@PathVariable String id) {
        return ApiResponse.<CultureArticleResponse>builder()
                .result(adminCultureService.getArticle(id))
                .build();
    }

    @PostMapping("/articles")
    public ApiResponse<CultureArticleResponse> createArticle(@Valid @RequestBody CultureArticleRequest request) {
        CultureArticleResponse response = adminCultureService.saveArticle(request);
        return ApiResponse.<CultureArticleResponse>builder()
                .result(response)
                .message("Lưu bài viết văn hóa thành công")
                .build();
    }

    @PutMapping("/articles/{id}")
    public ApiResponse<CultureArticleResponse> updateArticle(
            @PathVariable String id,
            @Valid @RequestBody CultureArticleRequest request
    ) {
        request.setId(id);
        CultureArticleResponse response = adminCultureService.saveArticle(request);
        return ApiResponse.<CultureArticleResponse>builder()
                .result(response)
                .message("Cập nhật bài viết văn hóa thành công")
                .build();
    }

    @PatchMapping("/articles/{id}/status")
    public ApiResponse<CultureArticleResponse> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.getOrDefault("status", "PUBLISHED");
        CultureArticleResponse response = adminCultureService.updateArticleStatus(id, status);
        return ApiResponse.<CultureArticleResponse>builder()
                .result(response)
                .message("Cập nhật trạng thái bài viết thành công")
                .build();
    }

    @DeleteMapping("/articles/{id}")
    public ApiResponse<Void> deleteArticle(@PathVariable String id) {
        adminCultureService.deleteArticle(id);
        return ApiResponse.<Void>builder()
                .message("Đã xóa bài viết văn hóa")
                .build();
    }

    // --- CATEGORIES ---

    @GetMapping("/categories")
    public ApiResponse<List<CultureCategoryResponse>> getAllCategories() {
        return ApiResponse.<List<CultureCategoryResponse>>builder()
                .result(adminCultureService.getAllCategories())
                .build();
    }

    @PostMapping("/categories")
    public ApiResponse<CultureCategoryResponse> createCategory(@Valid @RequestBody CultureCategoryRequest request) {
        CultureCategoryResponse response = adminCultureService.saveCategory(request);
        return ApiResponse.<CultureCategoryResponse>builder()
                .result(response)
                .message("Thêm danh mục văn hóa thành công")
                .build();
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<CultureCategoryResponse> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody CultureCategoryRequest request
    ) {
        request.setId(id);
        CultureCategoryResponse response = adminCultureService.saveCategory(request);
        return ApiResponse.<CultureCategoryResponse>builder()
                .result(response)
                .message("Cập nhật danh mục thành công")
                .build();
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        adminCultureService.deleteCategory(id);
        return ApiResponse.<Void>builder()
                .message("Đã xóa danh mục văn hóa")
                .build();
    }
}
