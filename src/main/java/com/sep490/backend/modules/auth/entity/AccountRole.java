package com.sep490.backend.modules.auth.entity;

import java.util.Locale;
import java.util.Set;

/**
 * The complete, fixed set of application account roles.
 *
 * <p>Permissions are held by the matching {@link Role} record, while an
 * account itself always stores exactly one value from this enum.</p>
 */
public enum AccountRole {
    LEARNER,
    CONTENT,
    ADMIN;

    private static final Set<String> ASSIGNABLE_BY_ADMIN = Set.of(LEARNER.name(), CONTENT.name(), ADMIN.name());

    public static String normalizeStored(String value) {
        if (value == null || value.isBlank()) {
            return LEARNER.name();
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ignored) {
            return LEARNER.name();
        }
    }

    public static String requireDefined(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Account role is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Unsupported account role: " + value);
        }
    }

    public static boolean isAssignableByAdmin(String value) {
        return ASSIGNABLE_BY_ADMIN.contains(value);
    }
}
