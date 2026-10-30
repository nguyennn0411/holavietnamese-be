package com.sep490.backend.modules.explore.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(170)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.destination.seed.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DestinationDemoSeedRunner implements CommandLineRunner {

    private final DestinationDemoSeed seed;

    @Override
    public void run(String... args) throws Exception {
        var result = seed.seed();

        log.info(
                "Destination seed: created {} destinations; updated {} destinations from JSON.",
                result.created(), result.updated()
        );
    }
}
