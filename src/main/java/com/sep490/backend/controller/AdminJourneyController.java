package com.sep490.backend.controller;

import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.JourneyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin")
public class AdminJourneyController {
    private final JourneyService service;
    @GetMapping("/journey")
    public ApiResponse<JourneyResponse.Configuration> configuration() { return ApiResponse.success(service.configuration()); }
    @PutMapping("/journey")
    public ApiResponse<JourneyResponse.Configuration> configure(@Valid @RequestBody JourneyRequest request) { return ApiResponse.success(service.configure(request)); }
    @GetMapping("/destinations/{id}/content")
    public ApiResponse<DestinationContentResponse> content(@PathVariable long id) { return ApiResponse.success(service.adminContent(id)); }
    @PutMapping("/destinations/{id}/content")
    public ApiResponse<DestinationContentResponse> link(@PathVariable long id, @Valid @RequestBody DestinationContentRequest request) { return ApiResponse.success(service.link(id, request)); }
}
