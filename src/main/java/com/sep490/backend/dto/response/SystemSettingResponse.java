package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class SystemSettingResponse {
    private Long id;
    private String key;
    private String value;
    private String description;
    private LocalDateTime updatedAt;
}
