package com.sep490.backend.controller;

import com.sep490.backend.dto.request.VocabularyTopicRequest;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.VocabularyTopicResponse;
import com.sep490.backend.service.VocabularyTopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/vocabulary-topics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVocabularyTopicController {

    private final VocabularyTopicService service;

    @GetMapping
    public ApiResponse<List<VocabularyTopicResponse>> findAll() {
        return ApiResponse.<List<VocabularyTopicResponse>>builder()
                .result(service.findAll())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<VocabularyTopicResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<VocabularyTopicResponse>builder()
                .result(service.findById(id))
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VocabularyTopicResponse> create(
            @Valid @RequestBody VocabularyTopicRequest request
    ) {
        return ApiResponse.<VocabularyTopicResponse>builder()
                .result(service.create(request))
                .message("Vocabulary topic created successfully.")
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<VocabularyTopicResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody VocabularyTopicRequest request
    ) {
        return ApiResponse.<VocabularyTopicResponse>builder()
                .result(service.update(id, request))
                .message("Vocabulary topic updated successfully.")
                .build();
    }
}