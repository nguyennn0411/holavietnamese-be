package com.sep490.backend.controller;

import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.DestinationResponse;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.service.DestinationService;
import com.sep490.backend.service.JourneyService;
import com.sep490.backend.config.LearnerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService service;
    private final JourneyService journey;

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
            @PathVariable Long id, @AuthenticationPrincipal LearnerPrincipal user
    ) {
        var destination = service.findPublishedById(id);
        journey.assertDestinationAccess(user == null ? null : user.id(), id);
        return ApiResponse.<DestinationResponse>builder()
                .result(destination)
                .build();
    }
}
