package com.sep490.backend.controller;

import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.VocabularyWordResponse;
import com.sep490.backend.service.VocabularyWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyCatalogController {

    private final VocabularyWordService service;

    @GetMapping
    public ApiResponse<List<VocabularyWordResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cefrLevel,
            @RequestParam(required = false) String partOfSpeech,
            @RequestParam(required = false) Long topicId
    ) {
        return ApiResponse.<List<VocabularyWordResponse>>builder()
                .result(
                        service.searchPublished(
                                keyword,
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
                .result(service.findPublishedById(id))
                .build();
    }
}