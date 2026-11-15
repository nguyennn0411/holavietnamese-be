package com.sep490.backend.modules.explore.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.repository.DestinationRepository;
import com.sep490.backend.modules.explore.service.AdminDestinationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests JSON seed validation, idempotent create/update behavior and startup delegation. */
class DestinationDemoSeedTests {
    private ObjectMapper json;
    private Validator validator;
    private DestinationRepository repository;
    private AdminDestinationService destinations;
    private DestinationDemoSeed seed;

    @BeforeEach
    void setUp() {
        json = mock(ObjectMapper.class);
        validator = mock(Validator.class);
        repository = mock(DestinationRepository.class);
        destinations = mock(AdminDestinationService.class);
        seed = new DestinationDemoSeed(json, validator, repository, destinations);
    }

    @Test
    void createsNewDestinationsAndUpdatesExistingSlugsCaseInsensitively() throws Exception {
        var existingRequest = request("  HA-NOI  ");
        var newRequest = request("hue");
        parse(List.of(existingRequest, newRequest));
        when(validator.validate(any(DestinationRequest.class))).thenReturn(Collections.emptySet());
        var existing = new Destination();
        existing.setId(15L);
        existing.setSlug("ha-noi");
        when(repository.search(null, null, null)).thenReturn(List.of(existing));

        var result = seed.seed();

        assertThat(result.created()).isOne();
        assertThat(result.updated()).isOne();
        verify(destinations).update(15L, existingRequest);
        verify(destinations).create(newRequest);
        verify(validator, times(2)).validate(any(DestinationRequest.class));
    }

    @Test
    void rejectsNullOrEmptyJsonBeforeTouchingRepository() throws Exception {
        parse(null);
        assertThatThrownBy(seed::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trống");

        parse(List.of());
        assertThatThrownBy(seed::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trống");

        verifyNoInteractions(repository, destinations);
    }

    @Test
    void rejectsNullDestinationAndValidationViolationsBeforeAnyWrite() throws Exception {
        parse(java.util.Arrays.asList((DestinationRequest) null));
        assertThatThrownBy(seed::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không hợp lệ");

        var invalid = request("invalid");
        parse(List.of(invalid));
        @SuppressWarnings("unchecked")
        ConstraintViolation<DestinationRequest> violation = mock(ConstraintViolation.class);
        when(validator.validate(invalid)).thenReturn(Set.of(violation));
        assertThatThrownBy(seed::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid");

        verifyNoInteractions(repository, destinations);
    }

    @Test
    void rejectsDuplicateSlugsAfterTrimAndLowercaseNormalization() throws Exception {
        parse(List.of(request("Da-Nang"), request("  da-nang  ")));
        when(validator.validate(any(DestinationRequest.class))).thenReturn(Collections.emptySet());

        assertThatThrownBy(seed::seed).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("da-nang");
        verifyNoInteractions(repository, destinations);
    }

    @Test
    void runnerExecutesSeedExactlyOnce() throws Exception {
        var delegatedSeed = mock(DestinationDemoSeed.class);
        when(delegatedSeed.seed()).thenReturn(new DestinationDemoSeed.SeedResult(2, 1));

        new DestinationDemoSeedRunner(delegatedSeed).run();

        verify(delegatedSeed).seed();
    }

    @SuppressWarnings("unchecked")
    private void parse(List<DestinationRequest> requests) throws Exception {
        when(json.readValue(any(InputStream.class), any(TypeReference.class))).thenReturn(requests);
    }

    private static DestinationRequest request(String slug) {
        return new DestinationRequest(
                "Tên Việt", "English name", slug, VietnamRegion.CENTRAL,
                null, null, null, null, null, DestinationStatus.PUBLISHED);
    }
}
