package com.sep490.backend.modules.admin.cms.dto.response;

import java.util.List;
import java.util.Map;

public record SiteContentResponse(
        List<SiteMenuItemResponse> menuItems,
        List<SiteBannerResponse> banners,
        Map<String, Boolean> features
) {
}
