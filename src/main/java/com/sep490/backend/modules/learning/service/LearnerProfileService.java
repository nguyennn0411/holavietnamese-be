package com.sep490.backend.modules.learning.service;

import com.sep490.backend.modules.auth.dto.request.OnboardingRequest;
import com.sep490.backend.modules.auth.dto.request.UserSettingsRequest;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.dto.response.UserProgressResponse;
import com.sep490.backend.modules.auth.dto.response.UserResponse;
import com.sep490.backend.modules.auth.port.LearnerProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LearnerProfileService implements LearnerProfileUseCase {
    private final UserProgressService userProgressService;
    private final AchievementService achievementService;

    @Override
    public UserResponse completeOnboarding(OnboardingRequest request) {
        return userProgressService.completeOnboarding(request);
    }

    @Override
    public UserResponse updateUserSettings(UserSettingsRequest request) {
        return userProgressService.updateUserSettings(request);
    }

    @Override
    public UserProgressResponse getUserProgress() {
        return userProgressService.getUserProgress();
    }

    @Override
    public List<BadgeResponse> getUserBadges() {
        return achievementService.getUserBadges();
    }
}
