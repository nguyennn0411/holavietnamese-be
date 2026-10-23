package com.sep490.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.entity.SiteBanner;
import com.sep490.backend.entity.SiteFeatureFlag;
import com.sep490.backend.entity.SiteMenuItem;
import com.sep490.backend.entity.enums.SiteBannerActionType;
import com.sep490.backend.entity.enums.SiteBannerPlacement;
import com.sep490.backend.entity.enums.SiteMenuPlacement;
import com.sep490.backend.repository.SiteBannerRepository;
import com.sep490.backend.repository.SiteFeatureFlagRepository;
import com.sep490.backend.repository.SiteMenuItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class SiteContentIntegrationTests {

    private static final LearnerPrincipal ADMIN =
            new LearnerPrincipal(9001L, "site-admin@test.local", "", true, "ADMIN");
    private static final LearnerPrincipal LEARNER =
            new LearnerPrincipal(9002L, "site-learner@test.local", "", true, "LEARNER");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SiteMenuItemRepository menus;
    @Autowired SiteBannerRepository banners;
    @Autowired SiteFeatureFlagRepository features;

    @Test
    void anonymousPublicConfigIsSortedAndFiltersDisabledScheduledAndFeatureLinkedContent() throws Exception {
        feature("COURSES", true);
        feature("BLOG", false);

        menu("GUEST_LATE", SiteMenuPlacement.GUEST_SIDEBAR, 20, true, null);
        menu("GUEST_EARLY", SiteMenuPlacement.GUEST_SIDEBAR, 10, true, "COURSES");
        menu("GUEST_DISABLED", SiteMenuPlacement.GUEST_SIDEBAR, 1, false, null);
        menu("GUEST_FEATURE_DISABLED", SiteMenuPlacement.GUEST_SIDEBAR, 2, true, "BLOG");
        menu("LEARNER_FIRST", SiteMenuPlacement.LEARNER_SIDEBAR, 5, true, null);

        Instant now = Instant.now();
        banner("GUEST_LATE_BANNER", 20, true, null,
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        banner("GUEST_EARLY_BANNER", 10, true, "COURSES", null, null);
        banner("GUEST_DISABLED_BANNER", 1, false, null, null, null);
        banner("GUEST_FEATURE_DISABLED_BANNER", 2, true, "BLOG", null, null);
        banner("GUEST_FUTURE_BANNER", 3, true, null,
                now.plus(1, ChronoUnit.DAYS), null);
        banner("GUEST_EXPIRED_BANNER", 4, true, null,
                null, now.minus(1, ChronoUnit.DAYS));

        mvc.perform(get("/api/site-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.features.COURSES").value(true))
                .andExpect(jsonPath("$.result.features.BLOG").value(false))
                .andExpect(jsonPath("$.result.menuItems.length()").value(3))
                .andExpect(jsonPath("$.result.menuItems[0].code").value("GUEST_EARLY"))
                .andExpect(jsonPath("$.result.menuItems[1].code").value("GUEST_LATE"))
                .andExpect(jsonPath("$.result.menuItems[2].code").value("LEARNER_FIRST"))
                .andExpect(jsonPath("$.result.banners.length()").value(2))
                .andExpect(jsonPath("$.result.banners[0].code").value("GUEST_EARLY_BANNER"))
                .andExpect(jsonPath("$.result.banners[1].code").value("GUEST_LATE_BANNER"));
    }

    @Test
    void onlyAdminsCanUseManagementEndpointsAndEveryToggleIsAudited() throws Exception {
        SiteFeatureFlag feature = feature("TOGGLE_FEATURE", true);
        SiteMenuItem menu = menu("TOGGLE_MENU", SiteMenuPlacement.GUEST_SIDEBAR, 10, true, feature.getKey());
        SiteBanner banner = banner("TOGGLE_BANNER", 10, true, feature.getKey(), null, null);

        mvc.perform(get("/api/admin/site-content/menus"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/site-content/menus").with(user(LEARNER)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/site-content/menus/{id}/enabled", menu.getId())
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enabled").value(false));
        mvc.perform(patch("/api/admin/site-content/banners/{id}/enabled", banner.getId())
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enabled").value(false));
        mvc.perform(patch("/api/admin/site-content/features/{key}/enabled", feature.getKey())
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.enabled").value(false));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action LIKE 'TOGGLE_SITE_%'",
                Integer.class)).isEqualTo(3);
        mvc.perform(get("/api/site-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.menuItems.length()").value(0))
                .andExpect(jsonPath("$.result.banners.length()").value(0))
                .andExpect(jsonPath("$.result.features.TOGGLE_FEATURE").value(false));
    }

    @Test
    void adminCrudUsesStablePathsAndRejectsInvalidContent() throws Exception {
        mvc.perform(post("/api/admin/site-content/features")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of(
                                "key", "NEW_FEATURE",
                                "nameVi", "Tính năng mới",
                                "nameEn", "New feature",
                                "description", "Managed in the database",
                                "enabled", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.key").value("NEW_FEATURE"));

        Map<String, Object> menuRequest = new LinkedHashMap<>();
        menuRequest.put("code", "NEW_MENU");
        menuRequest.put("placement", "GUEST_SIDEBAR");
        menuRequest.put("labelVi", "Khóa học");
        menuRequest.put("labelEn", "Courses");
        menuRequest.put("path", "/courses");
        menuRequest.put("iconKey", "book-open");
        menuRequest.put("displayOrder", 30);
        menuRequest.put("enabled", true);
        menuRequest.put("authRequired", false);
        menuRequest.put("mobilePrimary", false);
        menuRequest.put("featureKey", "NEW_FEATURE");

        MvcResult createdMenu = mvc.perform(post("/api/admin/site-content/menus")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(menuRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.code").value("NEW_MENU"))
                .andReturn();
        long menuId = result(createdMenu).path("id").asLong();

        menuRequest.put("labelEn", "All courses");
        menuRequest.put("displayOrder", 40);
        mvc.perform(put("/api/admin/site-content/menus/{id}", menuId)
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(menuRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.labelEn").value("All courses"))
                .andExpect(jsonPath("$.result.displayOrder").value(40));

        menuRequest.put("path", "courses-without-leading-slash");
        menuRequest.put("code", "INVALID_MENU_PATH");
        mvc.perform(post("/api/admin/site-content/menus")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(menuRequest)))
                .andExpect(status().isBadRequest());

        menuRequest.put("code", "CONTROL_CHARACTER_PATH");
        menuRequest.put("path", "/courses\n");
        mvc.perform(post("/api/admin/site-content/menus")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(menuRequest)))
                .andExpect(status().isBadRequest());

        Map<String, Object> bannerRequest = new LinkedHashMap<>();
        bannerRequest.put("code", "NEW_BANNER");
        bannerRequest.put("placement", "GUEST_HOME_HERO");
        bannerRequest.put("titleVi", "Học tiếng Việt");
        bannerRequest.put("titleEn", "Learn Vietnamese");
        bannerRequest.put("imageUrl", "/design/hero/slide1_welcome.jpg");
        bannerRequest.put("actionType", "INTERNAL_ROUTE");
        bannerRequest.put("actionLabelVi", "Bắt đầu");
        bannerRequest.put("actionLabelEn", "Start");
        bannerRequest.put("actionTarget", "/courses");
        bannerRequest.put("displayOrder", 10);
        bannerRequest.put("enabled", true);
        bannerRequest.put("featureKey", "NEW_FEATURE");

        MvcResult createdBanner = mvc.perform(post("/api/admin/site-content/banners")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(bannerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.code").value("NEW_BANNER"))
                .andReturn();
        long bannerId = result(createdBanner).path("id").asLong();

        bannerRequest.put("startsAt", "2030-01-02T00:00:00Z");
        bannerRequest.put("endsAt", "2030-01-01T00:00:00Z");
        bannerRequest.put("code", "INVALID_SCHEDULE");
        mvc.perform(post("/api/admin/site-content/banners")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(bannerRequest)))
                .andExpect(status().isBadRequest());

        bannerRequest.remove("startsAt");
        bannerRequest.remove("endsAt");
        bannerRequest.put("code", "UNSAFE_IMAGE_PATH");
        bannerRequest.put("imageUrl", "//untrusted.example/banner.jpg");
        mvc.perform(post("/api/admin/site-content/banners")
                        .with(user(ADMIN)).with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(bannerRequest)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/site-content/features/{key}", "NEW_FEATURE")
                        .with(user(ADMIN)).with(csrf()))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/admin/site-content/menus/{id}", menuId)
                        .with(user(ADMIN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/site-content/banners/{id}", bannerId)
                        .with(user(ADMIN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/admin/site-content/features/{key}", "NEW_FEATURE")
                        .with(user(ADMIN)).with(csrf()))
                .andExpect(status().isOk());
    }

    private JsonNode result(MvcResult response) throws Exception {
        return json.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private SiteFeatureFlag feature(String key, boolean enabled) {
        SiteFeatureFlag feature = new SiteFeatureFlag();
        feature.setKey(key);
        feature.setNameVi(key);
        feature.setNameEn(key);
        feature.setEnabled(enabled);
        return features.saveAndFlush(feature);
    }

    private SiteMenuItem menu(
            String code,
            SiteMenuPlacement placement,
            int order,
            boolean enabled,
            String featureKey
    ) {
        SiteMenuItem item = new SiteMenuItem();
        item.setCode(code);
        item.setPlacement(placement);
        item.setLabelVi(code);
        item.setLabelEn(code);
        item.setPath("/" + code.toLowerCase());
        item.setIconKey("home");
        item.setDisplayOrder(order);
        item.setEnabled(enabled);
        item.setAuthRequired(false);
        item.setMobilePrimary(false);
        item.setFeatureKey(featureKey);
        return menus.saveAndFlush(item);
    }

    private SiteBanner banner(
            String code,
            int order,
            boolean enabled,
            String featureKey,
            Instant startsAt,
            Instant endsAt
    ) {
        SiteBanner banner = new SiteBanner();
        banner.setCode(code);
        banner.setPlacement(SiteBannerPlacement.GUEST_HOME_HERO);
        banner.setTitleVi(code);
        banner.setTitleEn(code);
        banner.setImageUrl("/design/hero/slide1_welcome.jpg");
        banner.setActionType(SiteBannerActionType.NONE);
        banner.setDisplayOrder(order);
        banner.setEnabled(enabled);
        banner.setStartsAt(startsAt);
        banner.setEndsAt(endsAt);
        banner.setFeatureKey(featureKey);
        return banners.saveAndFlush(banner);
    }
}
