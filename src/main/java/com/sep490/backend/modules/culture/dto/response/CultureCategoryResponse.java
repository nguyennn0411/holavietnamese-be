package com.sep490.backend.modules.culture.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CultureCategoryResponse {
    private String id;
    private String name;
    private String nameVi;
    private String nameEn;
    private String slug;
    private String icon;
    private String description;
    private Integer displayOrder;
    private Long articleCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
