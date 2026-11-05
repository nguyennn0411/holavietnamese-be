package com.sep490.backend.modules.auth.bootstrap;

import com.sep490.backend.modules.auth.entity.Role;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Development-only role metadata and content-account bootstrap.
 *
 * <p>Deliberately does not create an ADMIN account. Administrators are
 * provisioned directly in the database, outside the application API and
 * bootstrap code.</p>
 */
@Component
@org.springframework.core.annotation.Order(0)
@org.springframework.context.annotation.Profile("dev-demo")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DataInitializer implements CommandLineRunner {

    RoleRepository roleRepository;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    Environment environment;

    @Override
    public void run(String... args) {
        String demoPassword = environment.getRequiredProperty("APP_DEMO_PASSWORD");
        int passwordBytes = demoPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (demoPassword.length() < 12 || passwordBytes > 72) {
            throw new IllegalStateException("APP_DEMO_PASSWORD must be at least 12 characters and at most 72 UTF-8 bytes");
        }

        try {
            initRole("ADMIN", "System administrator",
                    Set.of("USER_READ", "USER_WRITE", "ROLE_WRITE", "SETTING_WRITE", "ACHIEVEMENT_WRITE", "XP_RULE_WRITE", "AUDIT_READ"));
            initRole("LEARNER", "Vietnamese learner",
                    Set.of("PROFILE_READ", "PROFILE_WRITE", "LEARNING_READ"));
            initRole("CONTENT", "Learning-content manager",
                    Set.of("COURSE_READ", "COURSE_WRITE", "COURSE_SUBMIT",
                            "VOCABULARY_READ", "VOCABULARY_WRITE", "VOCABULARY_IMPORT",
                            "EXPLORE_READ", "EXPLORE_WRITE", "EXPLORE_LINK_WRITE"));

            // ADMIN accounts are intentionally never provisioned by application code.
            if (!userRepository.existsByUsername("contentmanager")) {
                User manager = new User();
                manager.setUsername("contentmanager");
                manager.setPasswordHash(passwordEncoder.encode(demoPassword));
                manager.setEmail("contentmanager@holavietnamese.com");
                manager.setFullName("Content Manager");
                manager.setStatus("ACTIVE");
                manager.setEnabled(true);
                manager.setEmailVerified(true);
                manager.setRole("CONTENT");
                userRepository.save(manager);
                log.info("Created the dev contentmanager account.");
            }
        } catch (Exception e) {
            log.warn("Unable to seed development roles/content account: {}", e.getMessage());
        }
    }

    private Role initRole(String name, String description, Set<String> permissions) {
        return roleRepository.findByName(name).map(role -> {
            if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
                role.setPermissions(new HashSet<>(permissions));
                return roleRepository.save(role);
            }
            return role;
        }).orElseGet(() -> {
            Role role = new Role();
            role.setName(name);
            role.setDescription(description);
            role.setPermissions(new HashSet<>(permissions));
            return roleRepository.save(role);
        });
    }
}
