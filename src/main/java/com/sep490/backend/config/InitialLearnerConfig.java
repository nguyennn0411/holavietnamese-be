package com.sep490.backend.config;


import com.sep490.backend.entity.UserJpaEntity;
import com.sep490.backend.repository.jpa.UserJpaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Optional first-account provisioning for a new installation; never updates an existing account. */
@Configuration
public class InitialLearnerConfig {
    @Bean
    @ConditionalOnProperty(name = "app.initial-learner.email")
    ApplicationRunner initialLearner(Environment environment, UserJpaRepository users, PasswordEncoder encoder) {
        return args -> {
            String email = environment.getRequiredProperty("app.initial-learner.email").trim();
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254)
                throw new IllegalArgumentException("Initial learner email is invalid.");
            if (users.findByEmail(email).isPresent()) return;
            String password = environment.getRequiredProperty("app.initial-learner.password");
            if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("Initial learner password must be at least 12 characters and at most 72 UTF-8 bytes.");
            var user = new UserJpaEntity();
            user.setEmail(email); user.setPasswordHash(encoder.encode(password)); user.setEnabled(true);
            users.save(user);
        };
    }
}
