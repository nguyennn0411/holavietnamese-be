package com.sep490.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Opt in with APP_DESTINATION_SEED_ENABLED=true or --app.destination.seed.enabled=true. */
@Component
@Order(120)
@ConditionalOnProperty(name = "app.destination.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DestinationDemoSeedRunner implements CommandLineRunner {
    private final DestinationDemoSeed seed;

    @Override
    public void run(String... args) throws Exception {
        log.info("Destination demo seed: created {} records; existing slugs preserved.", seed.seed());
    }
}
