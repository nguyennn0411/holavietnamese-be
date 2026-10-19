package com.sep490.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.backend.config.DestinationDemoSeed;
import com.sep490.backend.dto.request.DestinationRequest;
import com.sep490.backend.entity.enums.DestinationStatus;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.service.DestinationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DestinationDemoSeedIntegrationTests {
    @Autowired DestinationDemoSeed seed;
    @Autowired DestinationService service;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void sampleDataCoversRegionsStatusesAndBackendTimestamps() throws Exception {
        // Seeding is opt-in; a normal startup must not insert demo records.
        assertThat(service.search(null, null, null)).isEmpty();
        assertThat(seed.seed()).isEqualTo(12);
        var destinations = service.search(null, null, null);
        assertThat(destinations).hasSize(12);
        assertThat(destinations).extracting(d -> d.region()).contains(VietnamRegion.values());
        assertThat(destinations).extracting(d -> d.status()).contains(DestinationStatus.values());
        assertThat(service.searchPublished(null, null)).hasSize(9);
        for (var destination : destinations) {
            assertThat(destination.nameVi()).isNotBlank();
            assertThat(destination.nameEn()).isNotBlank();
            assertThat(destination.shortDescriptionVi()).isNotBlank();
            assertThat(destination.shortDescriptionEn()).isNotBlank();
            assertThat(destination.descriptionVi()).contains("\n\n");
            assertThat(destination.descriptionEn()).contains("\n\n");
            assertThat(destination.imageUrl()).startsWith("https://image.vietnam.travel/");
            assertThat(destination.createdAt()).isNotNull();
            assertThat(destination.updatedAt()).isNotNull();
            assertThat(destination.publishedAt() != null).isEqualTo(destination.status() == DestinationStatus.PUBLISHED);
            assertThat(destination.archivedAt() != null).isEqualTo(destination.status() == DestinationStatus.ARCHIVED);
        }
    }

    @Test
    void rerunningSeedPreservesEditsStatusesAndUnrelatedDestinations() throws Exception {
        seed.seed();
        var hanoi = service.search("Hanoi", null, null).getFirst();
        service.update(hanoi.id(), new DestinationRequest(
                "Hà Nội đã chỉnh sửa", hanoi.nameEn(), hanoi.slug(), hanoi.region(),
                hanoi.shortDescriptionVi(), hanoi.shortDescriptionEn(), "Nội dung riêng của Admin",
                hanoi.descriptionEn(), hanoi.imageUrl(), DestinationStatus.ARCHIVED));
        var edited = service.findById(hanoi.id());
        var custom = service.create(new DestinationRequest(
                "Điểm đến tự tạo", "Custom destination", "custom-destination", VietnamRegion.SOUTH,
                null, null, null, null, null, DestinationStatus.DRAFT));

        assertThat(seed.seed()).isZero();
        assertThat(service.search(null, null, null)).hasSize(13);
        assertThat(service.findById(hanoi.id())).isEqualTo(edited);
        assertThat(service.findById(custom.id())).isEqualTo(custom);
    }

    @Test
    void actualEndpointsFilterDataAndHideUnpublishedDetailsFromLearners() throws Exception {
        seed.seed();
        var learner = user("learner").roles("LEARNER");
        var admin = user("admin").roles("ADMIN");
        mvc.perform(get("/api/destinations").with(learner).param("region", "NORTH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(3));
        mvc.perform(get("/api/destinations").with(learner).param("keyword", "Hanoi").param("region", "NORTH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result[0].nameVi").value("Hà Nội"));
        mvc.perform(get("/api/admin/destinations").with(admin).param("status", "ARCHIVED").param("region", "SOUTH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].slug").value("phu-quoc"));
        mvc.perform(get("/api/admin/destinations").with(learner)).andExpect(status().isForbidden());

        for (var destination : service.search(null, null, null)) {
            var response = mvc.perform(get("/api/destinations/" + destination.id()).with(learner));
            if (destination.status() == DestinationStatus.PUBLISHED) {
                response.andExpect(status().isOk())
                        .andExpect(jsonPath("$.result.slug").value(destination.slug()));
                var result = json.readTree(response.andReturn().getResponse().getContentAsString()).path("result");
                assertThat(result.path("descriptionVi").asText()).isEqualTo(destination.descriptionVi());
                assertThat(result.path("descriptionEn").asText()).isEqualTo(destination.descriptionEn());
            } else {
                response.andExpect(status().isNotFound());
            }
        }
    }
}
