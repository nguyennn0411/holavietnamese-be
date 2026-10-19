package com.sep490.backend.dto.response;

import java.time.Instant;
import java.util.List;

public record JourneyResponse(List<Stop> stops, int totalStops, int completedStops,
                              Integer progressPercent, Long currentDestinationId,
                              boolean rulesConfigured, String blockingReason) {
    public record Stop(DestinationResponse destination, int displayOrder, boolean startPoint,
                       String status, boolean accessible, int progressPercent,
                       Instant startedAt, Instant completedAt, int requiredLessons,
                       int completedLessons, boolean canComplete, boolean passportStamped) {}
    public record AdminStop(DestinationResponse destination, int displayOrder, String status) {}
    public record Configuration(List<AdminStop> stops, String unlockRule, String completionRule) {}
    public record Passport(Long id, Long destinationId, String city, String cityEn,
                           boolean unlocked, Instant stampedAt) {}
}
