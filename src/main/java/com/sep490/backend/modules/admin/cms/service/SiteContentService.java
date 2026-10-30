package com.sep490.backend.modules.admin.cms.service;
import com.sep490.backend.modules.admin.service.AdminService;

import com.sep490.backend.modules.admin.cms.dto.request.SiteBannerRequest;
import com.sep490.backend.modules.admin.cms.dto.request.SiteFeatureFlagRequest;
import com.sep490.backend.modules.admin.cms.dto.request.SiteMenuItemRequest;
import com.sep490.backend.modules.admin.cms.dto.response.SiteBannerResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteContentResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteFeatureFlagResponse;
import com.sep490.backend.modules.admin.cms.dto.response.SiteMenuItemResponse;
import com.sep490.backend.modules.admin.cms.entity.SiteBanner;
import com.sep490.backend.modules.admin.cms.entity.SiteFeatureFlag;
import com.sep490.backend.modules.admin.cms.entity.SiteMenuItem;
import com.sep490.backend.modules.admin.cms.entity.enums.SiteBannerActionType;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.admin.cms.repository.SiteBannerRepository;
import com.sep490.backend.modules.admin.cms.repository.SiteFeatureFlagRepository;
import com.sep490.backend.modules.admin.cms.repository.SiteMenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteContentService {

    private static final Sort CONTENT_SORT = Sort.by(
            Sort.Order.asc("placement"),
            Sort.Order.asc("displayOrder"),
            Sort.Order.asc("id")
    );
    private static final Sort FEATURE_SORT = Sort.by(Sort.Order.asc("key"));

    private final SiteMenuItemRepository menuRepository;
    private final SiteBannerRepository bannerRepository;
    private final SiteFeatureFlagRepository featureRepository;
    private final AdminService adminService;
    private final Clock clock;

    public SiteContentResponse getPublicContent() {
        Map<String, Boolean> features = featureMap();
        Instant now = clock.instant();

        List<SiteMenuItemResponse> menuItems = menuRepository.findAll(CONTENT_SORT).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .filter(item -> !"/blog".equalsIgnoreCase(item.getPath()) && !"BLOG".equalsIgnoreCase(item.getFeatureKey()))
                .filter(item -> featureAllows(item.getFeatureKey(), features))
                .map(SiteContentService::toMenuResponse)
                .toList();

        List<SiteBannerResponse> banners = bannerRepository.findAll(CONTENT_SORT).stream()
                .filter(banner -> Boolean.TRUE.equals(banner.getEnabled()))
                .filter(banner -> featureAllows(banner.getFeatureKey(), features))
                .filter(banner -> isActiveAt(banner, now))
                .map(SiteContentService::toBannerResponse)
                .toList();

        return new SiteContentResponse(menuItems, banners, features);
    }

    public List<SiteMenuItemResponse> getMenus() {
        return menuRepository.findAll(CONTENT_SORT).stream()
                .map(SiteContentService::toMenuResponse)
                .toList();
    }

    public SiteMenuItemResponse getMenu(long id) {
        return toMenuResponse(findMenu(id));
    }

    @Transactional
    public SiteMenuItemResponse createMenu(SiteMenuItemRequest request) {
        String code = normalizeKey(request.code(), "Menu code");
        if (menuRepository.existsByCode(code)) {
            throw conflict("Menu code already exists.");
        }

        SiteMenuItem item = new SiteMenuItem();
        applyMenu(item, request, code);
        SiteMenuItem saved = menuRepository.saveAndFlush(item);
        audit("CREATE_SITE_MENU", "Site menu: " + code, "Created site menu item");
        return toMenuResponse(saved);
    }

    @Transactional
    public SiteMenuItemResponse updateMenu(long id, SiteMenuItemRequest request) {
        SiteMenuItem item = findMenu(id);
        String code = normalizeKey(request.code(), "Menu code");
        if (menuRepository.existsByCodeAndIdNot(code, id)) {
            throw conflict("Menu code already exists.");
        }

        applyMenu(item, request, code);
        SiteMenuItem saved = menuRepository.saveAndFlush(item);
        audit("UPDATE_SITE_MENU", "Site menu ID: " + id, "Updated site menu " + code);
        return toMenuResponse(saved);
    }

    @Transactional
    public SiteMenuItemResponse setMenuEnabled(long id, boolean enabled) {
        SiteMenuItem item = findMenu(id);
        item.setEnabled(enabled);
        SiteMenuItem saved = menuRepository.saveAndFlush(item);
        audit("TOGGLE_SITE_MENU", "Site menu ID: " + id, "Set enabled to " + enabled);
        return toMenuResponse(saved);
    }

    @Transactional
    public void deleteMenu(long id) {
        SiteMenuItem item = findMenu(id);
        menuRepository.delete(item);
        menuRepository.flush();
        audit("DELETE_SITE_MENU", "Site menu ID: " + id, "Deleted site menu " + item.getCode());
    }

    public List<SiteBannerResponse> getBanners() {
        return bannerRepository.findAll(CONTENT_SORT).stream()
                .map(SiteContentService::toBannerResponse)
                .toList();
    }

    public SiteBannerResponse getBanner(long id) {
        return toBannerResponse(findBanner(id));
    }

    @Transactional
    public SiteBannerResponse createBanner(SiteBannerRequest request) {
        String code = normalizeKey(request.code(), "Banner code");
        if (bannerRepository.existsByCode(code)) {
            throw conflict("Banner code already exists.");
        }

        SiteBanner banner = new SiteBanner();
        applyBanner(banner, request, code);
        SiteBanner saved = bannerRepository.saveAndFlush(banner);
        audit("CREATE_SITE_BANNER", "Site banner: " + code, "Created site banner");
        return toBannerResponse(saved);
    }

    @Transactional
    public SiteBannerResponse updateBanner(long id, SiteBannerRequest request) {
        SiteBanner banner = findBanner(id);
        String code = normalizeKey(request.code(), "Banner code");
        if (bannerRepository.existsByCodeAndIdNot(code, id)) {
            throw conflict("Banner code already exists.");
        }

        applyBanner(banner, request, code);
        SiteBanner saved = bannerRepository.saveAndFlush(banner);
        audit("UPDATE_SITE_BANNER", "Site banner ID: " + id, "Updated site banner " + code);
        return toBannerResponse(saved);
    }

    @Transactional
    public SiteBannerResponse setBannerEnabled(long id, boolean enabled) {
        SiteBanner banner = findBanner(id);
        banner.setEnabled(enabled);
        SiteBanner saved = bannerRepository.saveAndFlush(banner);
        audit("TOGGLE_SITE_BANNER", "Site banner ID: " + id, "Set enabled to " + enabled);
        return toBannerResponse(saved);
    }

    @Transactional
    public void deleteBanner(long id) {
        SiteBanner banner = findBanner(id);
        bannerRepository.delete(banner);
        bannerRepository.flush();
        audit("DELETE_SITE_BANNER", "Site banner ID: " + id, "Deleted site banner " + banner.getCode());
    }

    public List<SiteFeatureFlagResponse> getFeatures() {
        return featureRepository.findAll(FEATURE_SORT).stream()
                .map(SiteContentService::toFeatureResponse)
                .toList();
    }

    public SiteFeatureFlagResponse getFeature(String key) {
        return toFeatureResponse(findFeature(normalizeKey(key, "Feature key")));
    }

    @Transactional
    public SiteFeatureFlagResponse createFeature(SiteFeatureFlagRequest request) {
        String key = normalizeKey(request.key(), "Feature key");
        if (featureRepository.existsById(key)) {
            throw conflict("Feature key already exists.");
        }

        SiteFeatureFlag feature = new SiteFeatureFlag();
        applyFeature(feature, request, key);
        SiteFeatureFlag saved = featureRepository.saveAndFlush(feature);
        audit("CREATE_SITE_FEATURE", "Site feature: " + key, "Created site feature flag");
        return toFeatureResponse(saved);
    }

    @Transactional
    public SiteFeatureFlagResponse updateFeature(String key, SiteFeatureFlagRequest request) {
        String normalizedKey = normalizeKey(key, "Feature key");
        if (!normalizedKey.equals(normalizeKey(request.key(), "Feature key"))) {
            throw invalid("Feature key in the request must match the URL.");
        }

        SiteFeatureFlag feature = findFeature(normalizedKey);
        applyFeature(feature, request, normalizedKey);
        SiteFeatureFlag saved = featureRepository.saveAndFlush(feature);
        audit("UPDATE_SITE_FEATURE", "Site feature: " + normalizedKey, "Updated site feature flag");
        return toFeatureResponse(saved);
    }

    @Transactional
    public SiteFeatureFlagResponse setFeatureEnabled(String key, boolean enabled) {
        String normalizedKey = normalizeKey(key, "Feature key");
        SiteFeatureFlag feature = findFeature(normalizedKey);
        feature.setEnabled(enabled);
        SiteFeatureFlag saved = featureRepository.saveAndFlush(feature);
        audit("TOGGLE_SITE_FEATURE", "Site feature: " + normalizedKey, "Set enabled to " + enabled);
        return toFeatureResponse(saved);
    }

    @Transactional
    public void deleteFeature(String key) {
        String normalizedKey = normalizeKey(key, "Feature key");
        SiteFeatureFlag feature = findFeature(normalizedKey);
        if (menuRepository.existsByFeatureKey(normalizedKey)
                || bannerRepository.existsByFeatureKey(normalizedKey)) {
            throw conflict("Feature flag is still referenced by a menu item or banner.");
        }
        featureRepository.delete(feature);
        featureRepository.flush();
        audit("DELETE_SITE_FEATURE", "Site feature: " + normalizedKey, "Deleted site feature flag");
    }

    private void applyMenu(SiteMenuItem item, SiteMenuItemRequest request, String code) {
        rejectControlCharacters(request.path(), "Menu path");
        String path = optional(request.path());
        if (path != null) {
            validateInternalPath(path, "Menu path");
        }

        item.setCode(code);
        item.setPlacement(request.placement());
        item.setLabelVi(request.labelVi().strip());
        item.setLabelEn(request.labelEn().strip());
        item.setPath(path);
        item.setIconKey(optional(request.iconKey()));
        item.setDisplayOrder(request.displayOrder());
        item.setEnabled(request.enabled());
        item.setAuthRequired(request.authRequired());
        item.setMobilePrimary(request.mobilePrimary());
        item.setFeatureKey(resolveFeatureKey(request.featureKey()));
    }

    private void applyBanner(SiteBanner banner, SiteBannerRequest request, String code) {
        validateSchedule(request.startsAt(), request.endsAt());
        validateAssetUrl(request.imageUrl(), "Banner image URL");
        validateAction(request.actionType(), request.actionTarget(), request.actionLabelVi(), request.actionLabelEn());

        banner.setCode(code);
        banner.setPlacement(request.placement());
        banner.setEyebrowVi(optional(request.eyebrowVi()));
        banner.setEyebrowEn(optional(request.eyebrowEn()));
        banner.setTitleVi(request.titleVi().strip());
        banner.setTitleEn(request.titleEn().strip());
        banner.setDescriptionVi(optional(request.descriptionVi()));
        banner.setDescriptionEn(optional(request.descriptionEn()));
        banner.setImageUrl(request.imageUrl().strip());
        banner.setImageAltVi(optional(request.imageAltVi()));
        banner.setImageAltEn(optional(request.imageAltEn()));
        banner.setPhotoLabelVi(optional(request.photoLabelVi()));
        banner.setPhotoLabelEn(optional(request.photoLabelEn()));
        banner.setActionType(request.actionType());
        banner.setActionLabelVi(optional(request.actionLabelVi()));
        banner.setActionLabelEn(optional(request.actionLabelEn()));
        banner.setActionTarget(optional(request.actionTarget()));
        banner.setDisplayOrder(request.displayOrder());
        banner.setEnabled(request.enabled());
        banner.setStartsAt(request.startsAt());
        banner.setEndsAt(request.endsAt());
        banner.setFeatureKey(resolveFeatureKey(request.featureKey()));
    }

    private void applyFeature(SiteFeatureFlag feature, SiteFeatureFlagRequest request, String key) {
        feature.setKey(key);
        feature.setNameVi(request.nameVi().strip());
        feature.setNameEn(request.nameEn().strip());
        feature.setDescription(optional(request.description()));
        feature.setEnabled(request.enabled());
    }

    private Map<String, Boolean> featureMap() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        featureRepository.findAll(FEATURE_SORT)
                .forEach(feature -> features.put(feature.getKey(), Boolean.TRUE.equals(feature.getEnabled())));
        return features;
    }

    private String resolveFeatureKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = normalizeKey(rawKey, "Feature key");
        if (!featureRepository.existsById(key)) {
            throw LearningException.notFound("Site feature");
        }
        return key;
    }

    private SiteMenuItem findMenu(long id) {
        return menuRepository.findById(id)
                .orElseThrow(() -> LearningException.notFound("Site menu item"));
    }

    private SiteBanner findBanner(long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> LearningException.notFound("Site banner"));
    }

    private SiteFeatureFlag findFeature(String key) {
        return featureRepository.findById(key)
                .orElseThrow(() -> LearningException.notFound("Site feature"));
    }

    private void audit(String action, String target, String details) {
        adminService.logAdminAction(action, target, details);
    }

    private static boolean featureAllows(String key, Map<String, Boolean> features) {
        return key == null || Boolean.TRUE.equals(features.get(key));
    }

    private static boolean isActiveAt(SiteBanner banner, Instant now) {
        return (banner.getStartsAt() == null || !banner.getStartsAt().isAfter(now))
                && (banner.getEndsAt() == null || banner.getEndsAt().isAfter(now));
    }

    private static void validateSchedule(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw invalid("Banner end time must be after its start time.");
        }
    }

    private static void validateAction(
            SiteBannerActionType actionType,
            String rawTarget,
            String actionLabelVi,
            String actionLabelEn
    ) {
        rejectControlCharacters(rawTarget, "Banner action target");
        String target = optional(rawTarget);
        if (actionType != SiteBannerActionType.NONE
                && (optional(actionLabelVi) == null || optional(actionLabelEn) == null)) {
            throw invalid("Action labels are required when a banner has an action.");
        }
        if (actionType == SiteBannerActionType.INTERNAL_ROUTE
                && target == null) {
            throw invalid("Internal banner action target is required.");
        }
        if (actionType == SiteBannerActionType.INTERNAL_ROUTE) {
            validateInternalPath(target, "Internal banner action target");
        }
        if (actionType == SiteBannerActionType.EXTERNAL_URL) {
            validateExternalUrl(target, "External banner action target");
        }
    }

    private static void validateAssetUrl(String rawUrl, String label) {
        rejectControlCharacters(rawUrl, label);
        String url = rawUrl.strip();
        if (url.startsWith("/")) {
            validateInternalPath(url, label);
            return;
        }
        validateExternalUrl(url, label);
    }

    private static void validateInternalPath(String path, String label) {
        if (!path.startsWith("/")
                || path.startsWith("//")
                || path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)) {
            throw invalid(label + " must be a safe application path starting with a single '/'.");
        }
    }

    private static void rejectControlCharacters(String value, String label) {
        if (value != null && value.chars().anyMatch(Character::isISOControl)) {
            throw invalid(label + " must not contain control characters.");
        }
    }

    private static void validateExternalUrl(String rawUrl, String label) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw invalid(label + " is required.");
        }
        try {
            URI uri = URI.create(rawUrl.strip());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw invalid(label + " must be an HTTP or HTTPS URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(label + " must be a valid URL.");
        }
    }

    private static String normalizeKey(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid(label + " is required.");
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (!normalized.matches("[A-Z0-9_]+")) {
            throw invalid(label + " may contain only letters, numbers, spaces, hyphens, and underscores.");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static LearningException invalid(String message) {
        return new LearningException(LearningException.Kind.INVALID, message);
    }

    private static LearningException conflict(String message) {
        return new LearningException(LearningException.Kind.CONFLICT, message);
    }

    private static SiteMenuItemResponse toMenuResponse(SiteMenuItem item) {
        return new SiteMenuItemResponse(
                item.getId(),
                item.getCode(),
                item.getPlacement(),
                item.getLabelVi(),
                item.getLabelEn(),
                item.getPath(),
                item.getIconKey(),
                item.getDisplayOrder(),
                item.getEnabled(),
                item.getAuthRequired(),
                item.getMobilePrimary(),
                item.getFeatureKey()
        );
    }

    private static SiteBannerResponse toBannerResponse(SiteBanner banner) {
        return new SiteBannerResponse(
                banner.getId(),
                banner.getCode(),
                banner.getPlacement(),
                banner.getEyebrowVi(),
                banner.getEyebrowEn(),
                banner.getTitleVi(),
                banner.getTitleEn(),
                banner.getDescriptionVi(),
                banner.getDescriptionEn(),
                banner.getImageUrl(),
                banner.getImageAltVi(),
                banner.getImageAltEn(),
                banner.getPhotoLabelVi(),
                banner.getPhotoLabelEn(),
                banner.getActionType(),
                banner.getActionLabelVi(),
                banner.getActionLabelEn(),
                banner.getActionTarget(),
                banner.getDisplayOrder(),
                banner.getEnabled(),
                banner.getStartsAt(),
                banner.getEndsAt(),
                banner.getFeatureKey()
        );
    }

    private static SiteFeatureFlagResponse toFeatureResponse(SiteFeatureFlag feature) {
        return new SiteFeatureFlagResponse(
                feature.getKey(),
                feature.getNameVi(),
                feature.getNameEn(),
                feature.getDescription(),
                feature.getEnabled()
        );
    }
}
