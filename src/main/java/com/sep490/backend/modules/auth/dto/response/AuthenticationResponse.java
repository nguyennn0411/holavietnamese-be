package com.sep490.backend.modules.auth.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    String token;
    boolean authenticated;
    Long userId;
    String username;
    String fullName;
    Set<String> roles;
    Set<String> permissions;
}
