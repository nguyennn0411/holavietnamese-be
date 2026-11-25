package com.sep490.backend.modules.auth.seed;

import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DefaultAccountSeed {

    private static final String DEFAULT_PASSWORD = "123456";
    private static final List<AccountDefinition> ACCOUNTS = List.of(
            new AccountDefinition(
                    "admin", "admin@holavietnamese.com", "System Administrator", "ADMIN"),
            new AccountDefinition(
                    "content", "content@holavietnamese.com", "Content Creator", "CONTENT_CREATOR"),
            new AccountDefinition(
                    "learner", "learner@holavietnamese.com", "Default Learner", "LEARNER"));

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SeedResult seed() {
        int created = 0;
        int skipped = 0;

        for (AccountDefinition account : ACCOUNTS) {
            if (users.findByUsername(account.username()).isPresent()
                    || users.findByEmail(account.email()).isPresent()) {
                skipped++;
                continue;
            }

            User user = new User();
            user.setUsername(account.username());
            user.setEmail(account.email());
            user.setFullName(account.fullName());
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
            user.setRole(account.role());
            user.setStatus("ACTIVE");
            user.setEnabled(true);
            user.setEmailVerified(true);
            users.save(user);
            created++;
        }

        return new SeedResult(created, skipped);
    }

    public record SeedResult(int created, int skipped) {}

    private record AccountDefinition(
            String username, String email, String fullName, String role) {}
}
