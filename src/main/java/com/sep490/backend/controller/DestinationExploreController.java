package com.sep490.backend.controller;

import com.sep490.backend.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.DestinationExploreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class DestinationExploreController {
    private final DestinationExploreService service;

    @GetMapping("/api/destinations/{id}/content")
    public ApiResponse<DestinationExploreContentResponse> content(@PathVariable long id) {
        return ApiResponse.success(service.content(id,false));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/destinations/{id}/explore-content")
    public ApiResponse<DestinationExploreContentResponse> management(@PathVariable long id) {
        return ApiResponse.success(service.content(id,true));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/api/admin/destinations/{id}/explore-content")
    public ApiResponse<DestinationExploreContentResponse> save(@PathVariable long id,@Valid @RequestBody DestinationExploreContentRequest request) {
        return ApiResponse.success(service.save(id,request));
    }
}
