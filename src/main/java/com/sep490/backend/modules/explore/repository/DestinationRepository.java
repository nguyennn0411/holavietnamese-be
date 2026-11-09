package com.sep490.backend.modules.explore.repository;

import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public interface DestinationRepository
        extends JpaRepository<Destination, Long>, JpaSpecificationExecutor<Destination> {

    /** Kiem tra slug da ton tai, khong phan biet hoa thuong. */
    boolean existsBySlugIgnoreCase(String slug);

    /** Kiem tra trung slug khi sua, loai tru dia diem dang cap nhat. */
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    /** Kiem tra slug truoc khi tao dia diem. */
    default boolean existsBySlug(String slug) {
        return existsBySlugIgnoreCase(slug);
    }

    /** Kiem tra slug truoc khi cap nhat dia diem. */
    default boolean existsBySlugExceptId(String slug, Long id) {
        return existsBySlugIgnoreCaseAndIdNot(slug, id);
    }

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    default List<Destination> search(
            String keyword, DestinationStatus status, VietnamRegion region) {
        return findAll(
                        specification(keyword, status, region),
                        Sort.by(Sort.Order.asc("nameVi"), Sort.Order.asc("id")))
                .stream()
                .toList();
    }

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    default Page<Destination> searchPage(
            String keyword, DestinationStatus status, VietnamRegion region, int page, int size) {
        var pageable =
                PageRequest.of(
                        Math.max(0, page),
                        Math.max(1, Math.min(size, 100)),
                        Sort.by(Sort.Order.asc("nameVi"), Sort.Order.asc("id")));
        return findAll(specification(keyword, status, region), pageable);
    }

    /** Tao dieu kien truy van theo cac bo loc ma van giu thu tu sap xep cu. */
    private Specification<Destination> specification(
            String keyword, DestinationStatus status, VietnamRegion region) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("nameVi")), pattern),
                                cb.like(cb.lower(root.get("nameEn")), pattern)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (region != null) {
                predicates.add(cb.equal(root.get("region"), region));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
