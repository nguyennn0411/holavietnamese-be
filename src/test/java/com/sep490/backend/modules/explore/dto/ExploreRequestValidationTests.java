package com.sep490.backend.modules.explore.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.sep490.backend.modules.explore.dto.request.DestinationExploreContentRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationManagementRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Kiem tra cac rang buoc request truoc khi du lieu Explore di vao service. */
class ExploreRequestValidationTests {
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void regionalTranslationRequiresTheVietnameseExampleItTranslates() {
        var missingVietnamese = regional(null, "A southern greeting");
        var completePair = regional("Bạn khỏe không?", "How are you?");
        var noTranslation = regional(null, "   ");

        // Ban dich vi du khong co cau goc se lam FE hien thi mot cap noi dung vo nghia.
        assertThat(validator.validate(missingVietnamese))
                .anySatisfy(
                        violation ->
                                assertThat(violation.getPropertyPath().toString())
                                        .isEqualTo("exampleTranslationValid"));
        assertThat(validator.validate(completePair)).isEmpty();
        assertThat(validator.validate(noTranslation)).isEmpty();
    }

    @Test
    void managementRequestCascadesValidationIntoDestinationAndExploreContent() {
        var invalidDestination =
                new DestinationRequest(
                        " ", "Hanoi", "ha-noi", VietnamRegion.NORTH,
                        null, null, null, null, null, DestinationStatus.DRAFT);
        var invalidContent =
                new DestinationExploreContentRequest(
                        List.of(0L), List.of("culture id with spaces"), List.of(" "));
        var request = new DestinationManagementRequest(invalidDestination, invalidContent);

        var paths =
                validator.validate(request).stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .toList();

        // @Valid tren request cha phai bat ca loi cua object con va phan tu trong danh sach.
        assertThat(paths).contains(
                "destination.nameVi",
                "exploreContent.vocabularyIds[0].<list element>",
                "exploreContent.cultureIds[0].<list element>",
                "exploreContent.scenarioIds[0].<list element>");
    }

    @Test
    void validManagementRequestPassesAllNestedConstraints() {
        var destination =
                new DestinationRequest(
                        "Hà Nội", "Hanoi", "ha-noi", VietnamRegion.NORTH,
                        "Thủ đô", "Capital", "Mô tả", "Description",
                        "https://cdn.test/hanoi.jpg", DestinationStatus.DRAFT);
        var content =
                new DestinationExploreContentRequest(
                        List.of(1L, 2L), List.of("ao_dai", "water-puppet"), List.of("greeting"));

        assertThat(validator.validate(new DestinationManagementRequest(destination, content)))
                .isEmpty();
    }

    private static RegionalVocabularyRequest regional(String exampleVi, String exampleEn) {
        return new RegionalVocabularyRequest(
                1L,
                VietnamRegion.SOUTH,
                "bạn",
                "friend",
                "friend",
                null,
                null,
                exampleVi,
                exampleEn,
                null,
                null,
                null);
    }
}
