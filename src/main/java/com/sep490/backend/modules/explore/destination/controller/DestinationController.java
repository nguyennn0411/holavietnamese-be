package com.sep490.backend.modules.explore.destination.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.destination.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService service;

    @GetMapping
    public ApiResponse<List<DestinationResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region
    ) {
        return ApiResponse.<List<DestinationResponse>>builder()
                .result(service.searchPublished(keyword, region))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<DestinationResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.findPublishedById(id))
                .build();
    }
}
