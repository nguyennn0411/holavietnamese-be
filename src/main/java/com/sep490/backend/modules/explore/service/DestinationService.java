package com.sep490.backend.modules.explore.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.mapper.DestinationMapper;
import com.sep490.backend.modules.explore.repository.DestinationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {
    private final DestinationRepository repository;

    /** Chi tra du lieu da xuat ban cho nguoi hoc. */
    public List<DestinationResponse> searchPublished(String keyword, VietnamRegion region) {
        return repository.search(keyword, DestinationStatus.PUBLISHED, region).stream()
                .map(DestinationMapper::toResponse)
                .toList();
    }

    /** Tra chi tiet da xuat ban, bao loi neu noi dung chua duoc cong khai. */
    public DestinationResponse findPublishedById(Long id) {
        DestinationResponse destination =
                repository
                        .findById(id)
                        .map(DestinationMapper::toResponse)
                        .filter(item -> item.status() == DestinationStatus.PUBLISHED)
                        .orElseThrow(() -> LearningException.notFound("Destination"));

        return destination;
    }
}
