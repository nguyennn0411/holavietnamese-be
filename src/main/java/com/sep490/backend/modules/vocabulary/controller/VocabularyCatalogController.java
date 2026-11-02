
package com.sep490.backend.modules.vocabulary.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyWordResponse;
import com.sep490.backend.modules.vocabulary.service.VocabularyWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyCatalogController {

    private final VocabularyWordService service;

    // 1. Tìm kiếm từ vựng có phân trang
    @GetMapping("/search")
    public ApiResponse<org.springframework.data.domain.Page<VocabularyWordResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean exact) {

        return ApiResponse.success(
                service.searchPage(keyword, null, null, page, size, true, exact)
        );
    }

    // 2. Lấy thông tin liên kết của một từ vựng
    @GetMapping("/{id}/links")
    public ApiResponse<java.util.Map<String, Object>> links(@PathVariable Long id) {
        return ApiResponse.success(service.links(id));
    }

    // 3. Tìm kiếm danh sách từ vựng đã xuất bản
    @GetMapping
    public ApiResponse<List<VocabularyWordResponse>> search(
            @RequestParam(required = false) String keyword) {

        return ApiResponse.<List<VocabularyWordResponse>>builder()
                .result(service.searchPublished(keyword, null))
                .build();
    }

    // 4. Lấy chi tiết từ vựng đã xuất bản theo ID
    @GetMapping("/{id}")
    public ApiResponse<VocabularyWordResponse> findById(@PathVariable Long id) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.findPublishedById(id))
                .build();
    }
}
