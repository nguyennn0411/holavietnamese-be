package com.sep490.backend.modules.admin.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Deliberately narrow role-change contract. Arrays, aliases, ADMIN, and any
 * other fields are rejected before the service layer is reached.
 */
@Getter
@Setter
public class UpdateUserRoleRequest {

    @NotBlank(message = "role is required.")
    @Pattern(regexp = "LEARNER|CONTENT", message = "role must be LEARNER or CONTENT.")
    private String role;

    @JsonAnySetter
    public void rejectUnexpectedField(String name, Object value) {
        throw new IllegalArgumentException("Only the role field is accepted.");
    }
}
