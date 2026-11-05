package com.sep490.backend.modules.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Prevents clients from smuggling role/roles into profile or registration
 * payloads. Other unknown fields retain the project's existing lenient JSON
 * behavior; neither can ever alter the stored account role.
 */
public abstract class RoleMutationProtectedRequest {

    @JsonAnySetter
    public void rejectRoleMutationField(String field, Object value) {
        if ("role".equalsIgnoreCase(field) || "roles".equalsIgnoreCase(field)) {
            throw new IllegalArgumentException("Role may only be changed by an administrator through /api/admin/users/{id}/role.");
        }
    }
}
