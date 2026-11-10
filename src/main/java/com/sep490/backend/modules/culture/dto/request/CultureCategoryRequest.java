package com.sep490.backend.modules.culture.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CultureCategoryRequest {
    private String id;

    @NotBlank(message = "Tên danh mục không được để trống")
    private String name;

    private String nameVi;
    private String nameEn;
    private String slug;
    private String icon;
    private String description;
    private Integer displayOrder;
}
