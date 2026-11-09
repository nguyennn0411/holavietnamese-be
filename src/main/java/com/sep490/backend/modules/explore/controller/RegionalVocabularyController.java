package com.sep490.backend.modules.explore.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.RegionalVocabularyService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/regional-vietnamese")
public class RegionalVocabularyController {
    private final RegionalVocabularyService service;

    /** Lay danh sach theo bo loc cua nguoi dung. */
    @GetMapping
    public ApiResponse<List<RegionalVocabularyResponse.Comparison>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region) {
        return ApiResponse.success(service.comparisons(keyword, region));
    }

    /** Lay chi tiet theo ID va quyen truy cap hien tai. */
    @GetMapping("/{vocabularyId}")
    public ApiResponse<RegionalVocabularyResponse.Comparison> get(@PathVariable long vocabularyId) {
        return ApiResponse.success(service.comparison(vocabularyId));
    }
}
