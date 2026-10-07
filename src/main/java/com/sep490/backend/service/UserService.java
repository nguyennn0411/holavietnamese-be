package com.sep490.backend.service;

import com.sep490.backend.dto.request.ChangePasswordRequest;
import com.sep490.backend.dto.request.UserRegisterRequest;
import com.sep490.backend.dto.request.UserUpdateRequest;
import com.sep490.backend.dto.response.UserResponse;
import com.sep490.backend.entity.Role;
import com.sep490.backend.entity.User;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.RoleRepository;
import com.sep490.backend.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
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

    /**
     * Đăng ký tài khoản mới cho học viên nước ngoài học tiếng Việt.
     * Tự động đăng nhập và trả về JWT token luôn.
     */
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USERNAME_EXISTED);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }

        Role learnerRole = roleRepository.findByName("LEARNER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("LEARNER");
                    role.setDescription("Học viên học tiếng Việt");
                    return roleRepository.save(role);
                });

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
                ? request.getTargetLevel().trim() : "A1");
        user.setStatus("ACTIVE");
        user.setRoles(new HashSet<>(Set.of(learnerRole)));

        User savedUser = userRepository.save(user);
        log.info("Học viên [{}] đăng ký tài khoản thành công", savedUser.getUsername());

        // Sinh token tự động để học viên đăng nhập ngay lập tức
        String token = authenticationService.generateToken(savedUser);
        UserResponse response = toUserResponse(savedUser);
        response.setToken(token);

        return response;
    }

    /**
     * Lấy thông tin cá nhân của người dùng đang đăng nhập.
     */
    public UserResponse getMyProfile() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String username = authentication.getName();
        User user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return toUserResponse(user);
    }

    /**
     * Cập nhật thông tin cá nhân học viên.
     */
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
            user.setAvatarUrl(request.getAvatarUrl());
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
        log.info("Người dùng [{}] cập nhật thông tin cá nhân thành công", username);
        return toUserResponse(updatedUser);
    }

    /**
     * Đổi mật khẩu tài khoản.
     */
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
        log.info("Người dùng [{}] đổi mật khẩu thành công", username);
    }

    /**
     * Lấy danh sách tất cả học viên (dành cho Admin).
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !Boolean.TRUE.equals(u.getIsRemoved()))
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    private UserResponse toUserResponse(User user) {
        Set<String> roleNames = user.getRoles() == null || user.getRoles().isEmpty() ? Set.of(user.getRole()) :
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .nativeLanguage(user.getNativeLanguage())
                .learningGoal(user.getLearningGoal())
                .targetLevel(user.getTargetLevel())
                .status(user.getStatus())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
