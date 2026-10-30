package com.sep490.backend.modules.explore.destination.dto.request;

import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DestinationRequest(
        @NotBlank
        @Size(max = 150)
        String nameVi,

        @NotBlank
        @Size(max = 150)
        String nameEn,

        @NotBlank
        @Size(max = 180)
        String slug,

        @NotNull
        VietnamRegion region,

        @Size(max = 500)
        String shortDescriptionVi,

        @Size(max = 500)
        String shortDescriptionEn,

        String descriptionVi,

        String descriptionEn,

        @Size(max = 2048)
        String imageUrl,

        DestinationStatus status
) {
}
