package com.sep490.backend.modules.culture.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.culture.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.culture.entity.enums.VietnamRegion;
import com.sep490.backend.modules.culture.service.RegionalVocabularyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/regional-vietnamese")
public class RegionalVocabularyController {
    private final RegionalVocabularyService service;
    @GetMapping public ApiResponse<List<RegionalVocabularyResponse.Comparison>> list(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) VietnamRegion region) {
        return ApiResponse.success(service.comparisons(keyword, region));
    }
    @GetMapping("/{vocabularyId}") public ApiResponse<RegionalVocabularyResponse.Comparison> get(@PathVariable long vocabularyId) {
        return ApiResponse.success(service.comparison(vocabularyId));
    }
}
