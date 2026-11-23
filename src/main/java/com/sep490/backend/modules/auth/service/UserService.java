package com.sep490.backend.modules.auth.service;

import com.sep490.backend.common.dto.CloudinaryUploadSignatureResponse;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.media.CloudinarySignatureService;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.dto.request.AvatarUrlRequest;
import com.sep490.backend.modules.auth.dto.request.ChangePasswordRequest;
import com.sep490.backend.modules.auth.dto.request.UserRegisterRequest;
import com.sep490.backend.modules.auth.dto.request.UserUpdateRequest;
import com.sep490.backend.modules.auth.dto.response.UserResponse;
import com.sep490.backend.modules.auth.entity.AccountRole;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    AuthenticationService authenticationService;
    CloudinarySignatureService cloudinarySignatureService;
    NotificationPort notifications;
    PasswordPolicyValidator passwordPolicyValidator;

    /**
     * Public registration has no role input: every newly created account is a
     * LEARNER, regardless of anything submitted by a client.
     */
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        passwordPolicyValidator.validate(request.getPassword(), request.getUsername(), request.getEmail());

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setFullName(request.getFullName() != null && !request.getFullName().isBlank()
                ? request.getFullName().trim() : request.getUsername().trim());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setNativeLanguage(request.getNativeLanguage() != null && !request.getNativeLanguage().isBlank()
                ? request.getNativeLanguage().trim() : "en");
        user.setLearningGoal(request.getLearningGoal());
        user.setTargetLevel(request.getTargetLevel() != null && !request.getTargetLevel().isBlank()
                ? request.getTargetLevel().trim() : null);
        user.setStatus("ACTIVE");
        user.setEnabled(true);
        user.setRole(AccountRole.LEARNER.name());
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        String developmentOtp = authenticationService.sendRegistrationVerificationOtp(savedUser);
        log.info("Learner [{}] registered successfully", savedUser.getUsername());

        UserResponse response = toUserResponse(savedUser);
        response.setVerificationOtp(developmentOtp);
        return response;
    }

    public UserResponse getMyProfile() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User user = userRepository.findActiveByUsernameWithRoles(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UserUpdateRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAvatarUrl() != null) {
            if (!cloudinarySignatureService.isAllowedAvatarUrl(request.getAvatarUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Avatar URL must be a Cloudinary image from the configured cloud.");
            }
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getNativeLanguage() != null) {
            user.setNativeLanguage(request.getNativeLanguage());
        }
        if (request.getLearningGoal() != null) {
            user.setLearningGoal(request.getLearningGoal());
        }
        if (request.getTargetLevel() != null) {
            user.setTargetLevel(request.getTargetLevel());
        }

        User updatedUser = userRepository.save(user);
        log.info("User [{}] updated profile", username);
        return toUserResponse(updatedUser);
    }

    public CloudinaryUploadSignatureResponse createProfileAvatarUploadSignature() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return cloudinarySignatureService.createAvatarSignature(user.getId());
    }

    @Transactional
    public UserResponse updateProfileAvatarUrl(AvatarUrlRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!cloudinarySignatureService.isAllowedAvatarUrl(request.getAvatarUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Avatar URL must be a Cloudinary image from the configured cloud.");
        }
        user.setAvatarUrl(request.getAvatarUrl());

        User updatedUser = userRepository.save(user);
        log.info("User [{}] updated avatar", username);
        return toUserResponse(updatedUser);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new AppException(ErrorCode.PASSWORD_SAME_AS_CURRENT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        notifications.send(user.getId(), NotificationMessage.mandatory(
                "Password changed",
                "Your account password was changed. Contact support immediately if this was not you.",
                NotificationType.SECURITY,
                "/settings"));
        log.info("User [{}] changed password", username);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsRemoved()))
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    private UserResponse toUserResponse(User user) {
        String role = AccountRole.normalizeStored(user.getRole());
        Set<String> permissions = roleRepository.findByName(role)
                .map(found -> found.getPermissions() == null ? Set.<String>of() : Set.copyOf(found.getPermissions()))
                .orElseGet(Set::of);

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .country(user.getCountry())
                .nativeLanguage(user.getNativeLanguage())
                .learningGoal(user.getLearningGoal())
                .targetLevel(user.getTargetLevel())
                .dailyLearningGoalMinutes(user.getDailyLearningGoalMinutes())
                .audioSpeed(user.getAudioSpeed())
                .pronunciationHintsEnabled(user.getPronunciationHintsEnabled())
                .autoTranslateEnabled(user.getAutoTranslateEnabled())
                .notificationsEnabled(user.getNotificationsEnabled())
                .onboardingCompleted(user.getOnboardingCompleted())
                .emailVerified(user.getEmailVerified())
                .streakCount(user.getStreakCount())
                .totalXp(user.getTotalXp())
                .hearts(user.getHearts() != null ? user.getHearts() : 5)
                .status(user.getStatus())
                .role(role)
                .permissions(permissions)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
