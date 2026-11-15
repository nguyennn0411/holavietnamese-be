package com.sep490.backend.modules.auth.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.account.seed.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultAccountSeedRunner implements CommandLineRunner {

    private final DefaultAccountSeed seed;

    @Override
    public void run(String... args) {
        var result = seed.seed();
        log.info(
                "Default account seed: created {} accounts; skipped {} existing accounts.",
                result.created(),
                result.skipped());
    }
}
