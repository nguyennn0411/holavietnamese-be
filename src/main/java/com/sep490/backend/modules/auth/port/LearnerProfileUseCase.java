package com.sep490.backend.modules.auth.port;

import com.sep490.backend.modules.auth.dto.request.OnboardingRequest;
import com.sep490.backend.modules.auth.dto.request.UserSettingsRequest;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.dto.response.UserProgressResponse;
import com.sep490.backend.modules.auth.dto.response.UserResponse;

import java.util.List;

public interface LearnerProfileUseCase {
    UserResponse completeOnboarding(OnboardingRequest request);

    UserResponse updateUserSettings(UserSettingsRequest request);

    UserProgressResponse getUserProgress();

    List<BadgeResponse> getUserBadges();

    UserProgressResponse equipStreakFreeze();
}
