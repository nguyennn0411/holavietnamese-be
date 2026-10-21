package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.service.JourneyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Compatibility reads for Huy's Achievements and Nguyen's existing integration verifier. */
@RestController @RequiredArgsConstructor
public class LegacyJourneyReadController {
    private final JourneyService service;

    @GetMapping("/api/me/journey")
    public ApiResponse<JourneyResponse> history(@AuthenticationPrincipal LearnerPrincipal user,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) VietnamRegion region) {
        return ApiResponse.success(service.journey(userId(user),keyword,region));
    }
    @GetMapping("/api/me/journey/passport")
    public ApiResponse<List<JourneyResponse.Passport>> passport(@AuthenticationPrincipal LearnerPrincipal user) {
        return ApiResponse.success(service.passport(userId(user)));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/journey")
    public ApiResponse<JourneyResponse.Configuration> configuration() {
        return ApiResponse.success(service.configuration());
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/destinations/{id}/content")
    public ApiResponse<DestinationContentResponse> historicalLinks(@PathVariable long id) {
        return ApiResponse.success(service.adminContent(id));
    }

    @GetMapping("/api/me/journey/destinations/{id}/content")
    public void retiredContent() { throw retired(); }

    @PostMapping({"/api/me/journey/destinations/{id}/start", "/api/me/journey/destinations/{id}/complete"})
    public void retiredProgress() { throw retired(); }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping({"/api/admin/journey", "/api/admin/destinations/{id}/content"})
    public void retiredConfiguration() { throw retired(); }

    private static ResponseStatusException retired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "Luồng Journey cũ đã ngừng sử dụng. Khám phá dùng /api/destinations/{id}/content.");
    }
    private static long userId(LearnerPrincipal user) {
        if (user == null || user.id() == null)
            throw new LearningException(LearningException.Kind.FORBIDDEN,"Authenticated learner required.");
        return user.id();
    }
}
