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

    @GetMapping("/search")
    public ApiResponse<org.springframework.data.domain.Page<VocabularyWordResponse>> page(
            @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="false") boolean exact) {
        return ApiResponse.success(service.searchPage(keyword,null,null,page,size,true,exact));
    }
    @GetMapping("/{id}/links") public ApiResponse<java.util.Map<String,Object>> links(@PathVariable Long id) { return ApiResponse.success(service.links(id)); }

    @GetMapping
    public ApiResponse<List<VocabularyWordResponse>> search(
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.<List<VocabularyWordResponse>>builder()
                .result(
                        service.searchPublished(
                                keyword,null
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
