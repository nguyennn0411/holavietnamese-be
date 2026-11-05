package com.sep490.backend.common.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        // Never repair schema history automatically. Repair is an explicit
        // operational decision because it can conceal a migration mismatch.
        return flyway -> flyway.migrate();
    }
}
