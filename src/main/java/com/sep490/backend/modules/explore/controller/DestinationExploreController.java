package com.sep490.backend.modules.explore.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.dto.response.DestinationExploreContentResponse;
import com.sep490.backend.modules.explore.service.DestinationExploreService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class DestinationExploreController {
    private final DestinationExploreService service;

    /** Lay noi dung dia diem theo che do nguoi hoc hoac quan tri. */
    @GetMapping("/api/destinations/{id}/content")
    public ApiResponse<DestinationExploreContentResponse> content(@PathVariable long id) {
        return ApiResponse.success(service.content(id, false));
    }

    /** Lay noi dung lien ket de quan tri vien chinh sua. */
    @PreAuthorize(ContentPermissions.EXPLORE_READ_ACCESS)
    @GetMapping("/api/admin/destinations/{id}/explore-content")
    public ApiResponse<DestinationExploreContentResponse> management(@PathVariable long id) {
        return ApiResponse.success(service.content(id, true));
    }

    /** Kiem tra va luu cac lien ket theo dung thu tu trong request. */
    @PreAuthorize(ContentPermissions.EXPLORE_LINK_ACCESS)
    @PutMapping("/api/admin/destinations/{id}/explore-content")
    public ApiResponse<DestinationExploreContentResponse> save(
            @PathVariable long id, @Valid @RequestBody DestinationExploreContentRequest request) {
        return ApiResponse.success(service.save(id, request));
    }
}
