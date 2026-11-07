package com.sep490.backend.modules.explore.destination.service;

import com.sep490.backend.modules.explore.destination.dto.request.DestinationManagementRequest;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationManagementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationManagementService {
    private final DestinationService destinations;
    private final DestinationExploreService explore;

    /** Updates the destination and every Explore link as one atomic management action. */
    @Transactional
    public DestinationManagementResponse updateDraft(long id, DestinationManagementRequest request) {
        var destination = destinations.updateDraft(id, request.destination());
        var content = explore.save(id, request.exploreContent());
        return new DestinationManagementResponse(destination, content);
    }
}
