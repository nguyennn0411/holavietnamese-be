package com.sep490.backend.service;

import com.sep490.backend.dto.response.BadgeResponse;
import com.sep490.backend.entity.Badge;
import com.sep490.backend.entity.User;
import com.sep490.backend.entity.UserBadge;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.BadgeRepository;
import com.sep490.backend.repository.UserBadgeRepository;
import com.sep490.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BadgeResponse> getUserBadges() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<Badge> allBadges = badgeRepository.findByStatus("ACTIVE");
        List<UserBadge> unlockedBadges = userBadgeRepository.findByUserId(user.getId());

        Map<Long, UserBadge> userBadgeMap = unlockedBadges.stream()
                .collect(Collectors.toMap(ub -> ub.getBadge().getId(), ub -> ub));

        return allBadges.stream().map(badge -> {
            UserBadge ub = userBadgeMap.get(badge.getId());
            boolean isUnlocked = ub != null;
            return BadgeResponse.builder()
                    .id(badge.getId())
                    .code(badge.getCode())
                    .name(badge.getName())
                    .description(badge.getDescription())
                    .iconUrl(badge.getIconUrl())
                    .category(badge.getCategory())
                    .conditionType(badge.getConditionType())
                    .conditionValue(badge.getConditionValue())
                    .xpReward(badge.getXpReward())
                    .isUnlocked(isUnlocked)
                    .unlockedAt(isUnlocked ? ub.getUnlockedAt() : null)
                    .badgeLevel(isUnlocked ? ub.getBadgeLevel() : 1)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public Badge createBadge(Badge badge) {
        return badgeRepository.save(badge);
    }
}
