package com.sep490.backend.controller;

import com.sep490.backend.dto.request.DestinationRequest;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.DestinationResponse;
import com.sep490.backend.entity.enums.DestinationStatus;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.service.DestinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/destinations")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDestinationController {

    private final DestinationService service;

    @GetMapping
    public ApiResponse<List<DestinationResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DestinationStatus status,
            @RequestParam(required = false) VietnamRegion region
    ) {
        return ApiResponse.<List<DestinationResponse>>builder()
                .result(service.search(keyword, status, region))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<DestinationResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.findById(id))
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DestinationResponse> create(
            @Valid @RequestBody DestinationRequest request
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.create(request))
                .message("Destination created successfully.")
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<DestinationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DestinationRequest request
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.update(id, request))
                .message("Destination updated successfully.")
                .build();
    }
}