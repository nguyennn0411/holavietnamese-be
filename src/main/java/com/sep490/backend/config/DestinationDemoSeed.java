package com.sep490.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.dto.request.DestinationRequest;
import com.sep490.backend.repository.DestinationRepository;
import com.sep490.backend.service.DestinationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Inserts demo destinations through the existing service; existing slugs are never overwritten. */
@Component
@RequiredArgsConstructor
public class DestinationDemoSeed {
    private final ObjectMapper json;
    private final Validator validator;
    private final DestinationRepository repository;
    private final DestinationService service;

    @Transactional
    public int seed() throws IOException {
        List<DestinationRequest> destinations;
        try (var input = new ClassPathResource("demo/destinations.json").getInputStream()) {
            destinations = json.readValue(input, new TypeReference<>() {});
        }

        var slugs = new HashSet<String>();
        for (var destination : destinations) {
            if (!validator.validate(destination).isEmpty()) {
                throw new IllegalStateException("Invalid demo destination: " + destination.slug());
            }
            if (!slugs.add(destination.slug().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("Duplicate demo destination slug: " + destination.slug());
            }
        }

        int created = 0;
        for (var destination : destinations) {
            if (!repository.existsBySlug(destination.slug().trim().toLowerCase(Locale.ROOT))) {
                service.create(destination);
                created++;
            }
        }
        return created;
    }
}
