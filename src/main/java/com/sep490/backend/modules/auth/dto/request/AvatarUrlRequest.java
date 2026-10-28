package com.sep490.backend.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvatarUrlRequest {
    @NotBlank(message = "Avatar URL is required")
    @Size(max = 500, message = "Avatar URL must be 500 characters or fewer")
    String avatarUrl;
}
