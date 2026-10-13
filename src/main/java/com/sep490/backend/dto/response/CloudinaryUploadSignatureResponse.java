package com.sep490.backend.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudinaryUploadSignatureResponse {
    String cloudName;
    String apiKey;
    String uploadUrl;
    String folder;
    String publicId;
    Long timestamp;
    String signature;
}
