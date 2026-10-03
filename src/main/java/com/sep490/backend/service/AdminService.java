package com.sep490.backend.service;

import com.sep490.backend.dto.response.AdminDashboardStatsResponse;
import com.sep490.backend.dto.response.AdminUserResponse;
import com.sep490.backend.dto.response.AuditLogResponse;
import com.sep490.backend.entity.AuditLog;
import com.sep490.backend.entity.Role;
import com.sep490.backend.entity.User;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long activeLearners = userRepository.count(); // Active registered users
        long totalCourses = courseRepository.count();
        long totalLessons = lessonRepository.count();

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeLearners(activeLearners)
                .totalCourses(totalCourses)
                .totalLessons(totalLessons)
                .totalXpAwarded(1250) // Default stat
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToAdminUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return mapToAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse toggleUserStatus(Integer id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setStatus(status);
        userRepository.save(user);

        logAdminAction("TOGGLE_USER_STATUS", "User ID: " + id, "Set status to: " + status);
        return mapToAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateUserRoles(Integer id, Set<String> roleNames) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseGet(() -> {
                            Role r = new Role();
                            r.setName(name);
                            return roleRepository.save(r);
                        }))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        userRepository.save(user);

        logAdminAction("UPDATE_USER_ROLES", "User ID: " + id, "Updated roles: " + String.join(", ", roleNames));
        return mapToAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .adminUsername(log.getAdminUsername())
                        .action(log.getAction())
                        .target(log.getTarget())
                        .details(log.getDetails())
                        .ipAddress(log.getIpAddress())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public void logAdminAction(String action, String target, String details) {
        String admin = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";
        AuditLog auditLog = new AuditLog();
        auditLog.setAdminUsername(admin);
        auditLog.setAction(action);
        auditLog.setTarget(target);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }

    private AdminUserResponse mapToAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .nativeLanguage(user.getNativeLanguage())
                .learningGoal(user.getLearningGoal())
                .targetLevel(user.getTargetLevel())
                .country(user.getCountry())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .onboardingCompleted(user.getOnboardingCompleted())
                .streakCount(user.getStreakCount())
                .totalXp(user.getTotalXp())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }
}
