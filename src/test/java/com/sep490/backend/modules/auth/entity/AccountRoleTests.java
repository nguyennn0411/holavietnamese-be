package com.sep490.backend.modules.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.common.security.LearnerPrincipal;
import org.junit.jupiter.api.Test;

class AccountRoleTests {

    @Test
    void contentCreatorIsTheCanonicalAuthoringRole() {
        assertThat(AccountRole.requireDefined("CONTENT_CREATOR")).isEqualTo("CONTENT_CREATOR");
        assertThat(AccountRole.isAssignableByAdmin("CONTENT_CREATOR")).isTrue();
        assertThat(new LearnerPrincipal(1L, "creator@test.local", "", true, "CONTENT_CREATOR")
                .getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_CONTENT_CREATOR")
                .doesNotContain("ROLE_CONTENT");
    }

    @Test
    void legacyContentNamesNormalizeWithoutLosingExistingAccounts() {
        assertThat(AccountRole.normalizeStored("CONTENT")).isEqualTo("CONTENT_CREATOR");
        assertThat(AccountRole.normalizeStored("CONTENT_MANAGER")).isEqualTo("CONTENT_CREATOR");
    }
}
