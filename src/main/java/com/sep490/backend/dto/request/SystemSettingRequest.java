package com.sep490.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemSettingRequest {
    @NotBlank(message = "Setting key is required")
    @Size(max = 100, message = "Setting key must be at most 100 characters")
    private String key;

    @NotBlank(message = "Setting value is required")
    private String value;

    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;
}
