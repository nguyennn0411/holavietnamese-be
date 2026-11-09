package com.sep490.backend.modules.explore.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.explore.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.DestinationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService service;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    @GetMapping
    public ApiResponse<List<DestinationResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region) {
        return ApiResponse.<List<DestinationResponse>>builder()
                .result(service.searchPublished(keyword, region))
                .build();
    }

    /** Lay chi tiet noi dung theo ID. */
    @GetMapping("/{id}")
    public ApiResponse<DestinationResponse> findById(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.findPublishedById(id))
                .build();
    }
}
