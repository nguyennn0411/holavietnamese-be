package com.sep490.backend.modules.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Standard role-change contract for ADMIN, CONTENT, and LEARNER roles.
 */
@Getter
@Setter
public class UpdateUserRoleRequest {

    @NotBlank(message = "role is required.")
    @Pattern(regexp = "LEARNER|CONTENT|ADMIN", message = "role must be ADMIN, CONTENT, or LEARNER.")
    private String role;

    @JsonAnySetter
    public void rejectUnexpectedField(String name, Object value) {
        throw new IllegalArgumentException("Only the role field is accepted.");
    }
}
