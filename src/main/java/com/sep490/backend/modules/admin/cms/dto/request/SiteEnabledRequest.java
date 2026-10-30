package com.sep490.backend.modules.admin.cms.dto.request;

import jakarta.validation.constraints.NotNull;

public record SiteEnabledRequest(
        @NotNull(message = "Enabled is required")
        Boolean enabled
) {
}
