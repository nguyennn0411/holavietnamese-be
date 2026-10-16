package com.sep490.backend.controller;

import com.sep490.backend.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.enums.*;
import com.sep490.backend.service.RegionalVocabularyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/regional-language") @PreAuthorize("hasRole('ADMIN')")
public class AdminRegionalVocabularyController {
    private final RegionalVocabularyService service;
    @GetMapping public ApiResponse<List<RegionalVocabularyResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region, @RequestParam(required = false) Long vocabularyId,
            @RequestParam(required = false) VocabularyStatus status) {
        return ApiResponse.success(service.search(keyword, region, vocabularyId, status, false));
    }
    @GetMapping("/{id}") public ApiResponse<RegionalVocabularyResponse> get(@PathVariable long id) { return ApiResponse.success(service.get(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<RegionalVocabularyResponse> create(@Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.create(input));
    }
    @PutMapping("/{id}") public ApiResponse<RegionalVocabularyResponse> update(@PathVariable long id, @Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.update(id, input));
    }
}
