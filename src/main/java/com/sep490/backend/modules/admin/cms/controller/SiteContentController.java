package com.sep490.backend.modules.admin.cms.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteContentResponse;
import com.sep490.backend.modules.admin.cms.service.SiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site-content")
@RequiredArgsConstructor
public class SiteContentController {

    private final SiteContentService service;

    @GetMapping
    public ApiResponse<SiteContentResponse> getSiteContent() {
        return ApiResponse.success(service.getPublicContent());
    }
}
