package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.service.JourneyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/me/journey")
public class JourneyController {
    private final JourneyService service;
    @GetMapping
    public ApiResponse<JourneyResponse> journey(@AuthenticationPrincipal LearnerPrincipal user,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) VietnamRegion region) {
        return ApiResponse.success(service.journey(userId(user), keyword, region));
    }
    @GetMapping("/passport")
    public ApiResponse<List<JourneyResponse.Passport>> passport(@AuthenticationPrincipal LearnerPrincipal user) {
        return ApiResponse.success(service.passport(userId(user)));
    }
    @GetMapping("/destinations/{id}/content")
    public ApiResponse<DestinationContentResponse> content(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.learnerContent(userId(user), id));
    }
    @PostMapping("/destinations/{id}/complete")
    public ApiResponse<JourneyResponse> complete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable long id) {
        long learner = userId(user); service.complete(learner, id);
        return ApiResponse.success(service.journey(learner, null, null));
    }
    @PostMapping("/destinations/{id}/start")
    public ApiResponse<DestinationContentResponse> start(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable long id) {
        return ApiResponse.success(service.start(userId(user), id));
    }
    private static long userId(LearnerPrincipal user) {
        if (user == null || user.id() == null) throw new LearningException(LearningException.Kind.FORBIDDEN, "Authenticated learner required.");
        return user.id();
    }
}
