package com.sep490.backend.modules.explore.regional.controller.admin;

import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.modules.explore.regional.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.explore.regional.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.explore.regional.service.RegionalVocabularyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/admin/regional-language")
public class AdminRegionalVocabularyController {
    private final RegionalVocabularyService service;
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping public ApiResponse<List<RegionalVocabularyResponse>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region, @RequestParam(required = false) Long vocabularyId,
            @RequestParam(required = false) VocabularyStatus status) {
        return ApiResponse.success(service.search(keyword, region, vocabularyId, status, false));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping("/{id}") public ApiResponse<RegionalVocabularyResponse> get(@PathVariable long id) { return ApiResponse.success(service.get(id)); }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ApiResponse<RegionalVocabularyResponse> create(@Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.createDraft(input));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PutMapping("/{id}") public ApiResponse<RegionalVocabularyResponse> update(@PathVariable long id, @Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.updateDraft(id, input));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/submit-review") public ApiResponse<RegionalVocabularyResponse> submitReview(@PathVariable long id) {
        return ApiResponse.success(service.submitReview(id));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/archive") public ApiResponse<RegionalVocabularyResponse> archive(@PathVariable long id) {
        return ApiResponse.success(service.archive(id));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/restore") public ApiResponse<RegionalVocabularyResponse> restore(@PathVariable long id) {
        return ApiResponse.success(service.restore(id));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/revise") public ApiResponse<RegionalVocabularyResponse> revise(@PathVariable long id) {
        return ApiResponse.success(service.revise(id));
    }
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    @PostMapping("/{id}/review") public ApiResponse<RegionalVocabularyResponse> review(@PathVariable long id,
            @Valid @RequestBody ContentReviewRequest input) {
        return ApiResponse.success(service.review(id,input.decision()));
    }
}
