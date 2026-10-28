package com.sep490.backend.modules.explore.seed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.explore.destination.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.destination.repository.DestinationRepository;
import com.sep490.backend.modules.explore.destination.service.DestinationService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DestinationDemoSeed {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final DestinationRepository repository;
    private final DestinationService service;

    @Transactional
    public SeedResult seed() throws IOException {
        List<DestinationRequest> destinations;

        try (var input = new ClassPathResource(
                "demo/destinations.json"
        ).getInputStream()) {
            destinations = objectMapper.readValue(
                    input,
                    new TypeReference<List<DestinationRequest>>() {}
            );
        }

        if (destinations == null || destinations.isEmpty()) {
            throw new IllegalStateException(
                    "File dữ liệu điểm đến đang trống."
            );
        }

        // Kiểm tra toàn bộ dữ liệu trước khi bắt đầu lưu.
        var slugs = new HashSet<String>();

        for (var destination : destinations) {
            if (destination == null) {
                throw new IllegalStateException(
                        "File có điểm đến không hợp lệ."
                );
            }

            var violations = validator.validate(destination);

            if (!violations.isEmpty()) {
                throw new IllegalStateException(
                        "Dữ liệu điểm đến không hợp lệ: "
                                + destination.slug()
                                + " — "
                                + violations
                );
            }

            String slug = normalizeSlug(destination.slug());

            if (!slugs.add(slug)) {
                throw new IllegalStateException(
                        "Slug bị trùng trong file JSON: " + slug
                );
            }
        }

        int created = 0;
        int updated = 0;
        var existingBySlug = repository.search(null, null, null).stream()
                .collect(Collectors.toMap(
                        item -> normalizeSlug(item.slug()),
                        item -> item.id()
                ));

        for (var destination : destinations) {
            String slug = normalizeSlug(destination.slug());

            // JSON là nguồn dữ liệu cho các slug có trong file.
            // Cập nhật tại chỗ để giữ ID và các liên kết hiện có.
            Long existingId = existingBySlug.get(slug);
            if (existingId != null) {
                service.update(existingId, destination);
                updated++;
            } else {
                service.create(destination);
                created++;
            }
        }

        return new SeedResult(created, updated);
    }

    public record SeedResult(int created, int updated) {}

    private static String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
