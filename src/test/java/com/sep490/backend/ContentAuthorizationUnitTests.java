package com.sep490.backend;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.controller.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAuthorizationUnitTests {
    @Test
    void principalKeepsAllDatabaseRolesAndPermissions() {
        var principal = new LearnerPrincipal(1L, "mixed@example.test", "", true, "ADMIN",
                Set.of("ADMIN", "CONTENT_MANAGER"), Set.of("VOCABULARY_WRITE", "EXPLORE_READ"));

        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_CONTENT_MANAGER", "ROLE_LEARNER",
                        "VOCABULARY_WRITE", "EXPLORE_READ");
    }

    @Test
    void administratorIsLimitedToReadAndReviewWhileContentManagersKeepWriteActions() {
        for (var controller : Set.of(AdminVocabularyController.class, AdminDestinationController.class,
                AdminRegionalVocabularyController.class, ContentImportController.class,
                DestinationExploreController.class)) {
            for (var method : controller.getDeclaredMethods()) {
                var rule = method.getAnnotation(PreAuthorize.class);
                if (rule == null) continue;
                if (method.getName().equals("review"))
                    assertThat(rule.value()).contains("hasRole('ADMIN')").containsAnyOf("VOCABULARY_REVIEW", "EXPLORE_REVIEW");
                if (Set.of("create", "update", "save", "submitReview", "revise", "archive", "restore").contains(method.getName()))
                    assertThat(rule.value()).doesNotContain("hasRole('ADMIN')", "hasAnyRole('ADMIN'");
            }
        }
    }
}
