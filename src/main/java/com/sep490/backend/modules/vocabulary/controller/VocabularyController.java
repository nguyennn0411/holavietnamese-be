package com.sep490.backend.modules.vocabulary.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.service.VocabularyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService service;

    /** Tim kiem va phan trang du lieu theo bo loc tu request. */
    @GetMapping("/search")
    public ApiResponse<org.springframework.data.domain.Page<VocabularyResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean exact) {

        return ApiResponse.success(service.searchPage(keyword, page, size, exact));
    }

    /** Lay cac tu cung cach viet va cac tu lien quan da xuat ban. */
    @GetMapping("/{id}/links")
    public ApiResponse<java.util.Map<String, Object>> links(@PathVariable Long id) {
        return ApiResponse.success(service.links(id));
    }

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    @GetMapping
    public ApiResponse<List<VocabularyResponse>> search(
            @RequestParam(required = false) String keyword) {

        return ApiResponse.<List<VocabularyResponse>>builder()
                .result(service.searchPublished(keyword, null))
                .build();
    }

    /** Lay chi tiet noi dung theo ID. */
    @GetMapping("/{id}")
    public ApiResponse<VocabularyResponse> findById(@PathVariable Long id) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.findPublishedById(id))
                .build();
    }
}
