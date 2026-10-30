package com.sep490.backend.modules.explore.destination.repository;

import com.sep490.backend.modules.explore.destination.dto.model.Destination;
import com.sep490.backend.modules.explore.destination.entity.DestinationJpaEntity;
import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.destination.mapper.DestinationMapper;
import com.sep490.backend.modules.explore.destination.repository.jpa.DestinationJpaRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DestinationRepository {

    private final DestinationJpaRepository repository;

    public Optional<Destination> findById(Long id) {
        return repository.findById(id)
                .map(DestinationMapper::toDomain);
    }

    public boolean existsBySlug(String slug) {
        return repository.existsBySlugIgnoreCase(slug);
    }

    public boolean existsBySlugExceptId(String slug, Long id) {
        return repository.existsBySlugIgnoreCaseAndIdNot(slug, id);
    }

    public List<Destination> search(
            String keyword,
            DestinationStatus status,
            VietnamRegion region
    ) {
        Specification<DestinationJpaEntity> specification =
                (root, query, cb) -> {

                    List<Predicate> predicates = new ArrayList<>();

                    if (keyword != null && !keyword.isBlank()) {
                        String pattern = "%"
                                + keyword.trim().toLowerCase(Locale.ROOT)
                                + "%";

                        predicates.add(cb.or(
                                cb.like(
                                        cb.lower(root.get("nameVi")),
                                        pattern
                                ),
                                cb.like(
                                        cb.lower(root.get("nameEn")),
                                        pattern
                                )
                        ));
                    }

                    if (status != null) {
                        predicates.add(
                                cb.equal(root.get("status"), status)
                        );
                    }

                    if (region != null) {
                        predicates.add(
                                cb.equal(root.get("region"), region)
                        );
                    }

                    return cb.and(
                            predicates.toArray(Predicate[]::new)
                    );
                };

        return repository.findAll(
                        specification,
                        Sort.by(
                                Sort.Order.asc("nameVi"),
                                Sort.Order.asc("id")
                        )
                )
                .stream()
                .map(DestinationMapper::toDomain)
                .toList();
    }

    public Destination save(Destination destination) {
        DestinationJpaEntity entity = destination.id() == null
                ? new DestinationJpaEntity()
                : repository.findById(destination.id()).orElseThrow();

        entity.setNameVi(destination.nameVi());
        entity.setNameEn(destination.nameEn());
        entity.setSlug(destination.slug());
        entity.setRegion(destination.region());

        entity.setShortDescriptionVi(
                destination.shortDescriptionVi()
        );
        entity.setShortDescriptionEn(
                destination.shortDescriptionEn()
        );

        entity.setDescriptionVi(destination.descriptionVi());
        entity.setDescriptionEn(destination.descriptionEn());
        entity.setImageUrl(destination.imageUrl());

        entity.setStatus(destination.status());
        entity.setPublishedAt(destination.publishedAt());
        entity.setArchivedAt(destination.archivedAt());

        return DestinationMapper.toDomain(
                repository.saveAndFlush(entity)
        );
    }
}
