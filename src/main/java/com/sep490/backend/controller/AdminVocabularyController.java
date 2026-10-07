package com.sep490.backend.controller;

import com.sep490.backend.dto.request.VocabularyWordRequest;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.VocabularyWordResponse;
import com.sep490.backend.entity.enums.VocabularyStatus;
import com.sep490.backend.service.VocabularyWordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/vocabulary")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final VocabularyWordService service;

    @GetMapping
    public ApiResponse<List<VocabularyWordResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String cefrLevel,
            @RequestParam(required = false) String partOfSpeech,
            @RequestParam(required = false) Long topicId
    ) {
        return ApiResponse.<List<VocabularyWordResponse>>builder()
                .result(
                        service.search(
                                keyword,
                                status,
                                cefrLevel,
                                partOfSpeech,
                                topicId
                        )
                )
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<VocabularyWordResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.findById(id))
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VocabularyWordResponse> create(
            @Valid @RequestBody VocabularyWordRequest request
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.create(request))
                .message("Vocabulary created successfully.")
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<VocabularyWordResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody VocabularyWordRequest request
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.update(id, request))
                .message("Vocabulary updated successfully.")
                .build();
    }
}