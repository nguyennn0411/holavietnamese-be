package com.sep490.backend.modules.admin.cms.controller;

import com.sep490.backend.modules.admin.cms.dto.request.SiteBannerRequest;
import com.sep490.backend.modules.admin.cms.dto.request.SiteEnabledRequest;
import com.sep490.backend.modules.admin.cms.dto.request.SiteFeatureFlagRequest;
import com.sep490.backend.modules.admin.cms.dto.request.SiteMenuItemRequest;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteBannerResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteFeatureFlagResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteMenuItemResponse;
import com.sep490.backend.modules.admin.cms.service.SiteContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/site-content")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSiteContentController {

    private final SiteContentService service;

    @GetMapping("/menus")
    public ApiResponse<List<SiteMenuItemResponse>> getMenus() {
        return ApiResponse.success(service.getMenus());
    }

    @GetMapping("/menus/{id}")
    public ApiResponse<SiteMenuItemResponse> getMenu(@PathVariable long id) {
        return ApiResponse.success(service.getMenu(id));
    }

    @PostMapping("/menus")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SiteMenuItemResponse> createMenu(@Valid @RequestBody SiteMenuItemRequest request) {
        return ApiResponse.success("Menu item created", service.createMenu(request));
    }

    @PutMapping("/menus/{id}")
    public ApiResponse<SiteMenuItemResponse> updateMenu(
            @PathVariable long id,
            @Valid @RequestBody SiteMenuItemRequest request
    ) {
        return ApiResponse.success("Menu item updated", service.updateMenu(id, request));
    }

    @PatchMapping("/menus/{id}/enabled")
    public ApiResponse<SiteMenuItemResponse> setMenuEnabled(
            @PathVariable long id,
            @Valid @RequestBody SiteEnabledRequest request
    ) {
        return ApiResponse.success("Menu visibility updated", service.setMenuEnabled(id, request.enabled()));
    }

    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@PathVariable long id) {
        service.deleteMenu(id);
        return ApiResponse.success("Menu item deleted");
    }

    @GetMapping("/banners")
    public ApiResponse<List<SiteBannerResponse>> getBanners() {
        return ApiResponse.success(service.getBanners());
    }

    @GetMapping("/banners/{id}")
    public ApiResponse<SiteBannerResponse> getBanner(@PathVariable long id) {
        return ApiResponse.success(service.getBanner(id));
    }

    @PostMapping("/banners")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SiteBannerResponse> createBanner(@Valid @RequestBody SiteBannerRequest request) {
        return ApiResponse.success("Banner created", service.createBanner(request));
    }

    @PutMapping("/banners/{id}")
    public ApiResponse<SiteBannerResponse> updateBanner(
            @PathVariable long id,
            @Valid @RequestBody SiteBannerRequest request
    ) {
        return ApiResponse.success("Banner updated", service.updateBanner(id, request));
    }

    @PatchMapping("/banners/{id}/enabled")
    public ApiResponse<SiteBannerResponse> setBannerEnabled(
            @PathVariable long id,
            @Valid @RequestBody SiteEnabledRequest request
    ) {
        return ApiResponse.success("Banner visibility updated", service.setBannerEnabled(id, request.enabled()));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable long id) {
        service.deleteBanner(id);
        return ApiResponse.success("Banner deleted");
    }

    @GetMapping("/features")
    public ApiResponse<List<SiteFeatureFlagResponse>> getFeatures() {
        return ApiResponse.success(service.getFeatures());
    }

    @GetMapping("/features/{key}")
    public ApiResponse<SiteFeatureFlagResponse> getFeature(@PathVariable String key) {
        return ApiResponse.success(service.getFeature(key));
    }

    @PostMapping("/features")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SiteFeatureFlagResponse> createFeature(@Valid @RequestBody SiteFeatureFlagRequest request) {
        return ApiResponse.success("Feature flag created", service.createFeature(request));
    }

    @PutMapping("/features/{key}")
    public ApiResponse<SiteFeatureFlagResponse> updateFeature(
            @PathVariable String key,
            @Valid @RequestBody SiteFeatureFlagRequest request
    ) {
        return ApiResponse.success("Feature flag updated", service.updateFeature(key, request));
    }

    @PatchMapping("/features/{key}/enabled")
    public ApiResponse<SiteFeatureFlagResponse> setFeatureEnabled(
            @PathVariable String key,
            @Valid @RequestBody SiteEnabledRequest request
    ) {
        return ApiResponse.success("Feature flag updated", service.setFeatureEnabled(key, request.enabled()));
    }

    @DeleteMapping("/features/{key}")
    public ApiResponse<Void> deleteFeature(@PathVariable String key) {
        service.deleteFeature(key);
        return ApiResponse.success("Feature flag deleted");
    }
}
