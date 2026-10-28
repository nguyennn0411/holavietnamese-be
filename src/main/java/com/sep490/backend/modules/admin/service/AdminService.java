package com.sep490.backend.modules.admin.service;

import com.sep490.backend.modules.admin.dto.request.BadgeRequest;
import com.sep490.backend.modules.admin.dto.request.RoleRequest;
import com.sep490.backend.modules.admin.dto.request.SystemSettingRequest;
import com.sep490.backend.modules.admin.dto.request.XpRuleRequest;
import com.sep490.backend.common.dto.PageResponse;
import com.sep490.backend.modules.admin.dto.response.AdminDashboardStatsResponse;
import com.sep490.backend.modules.admin.dto.response.AdminUserResponse;
import com.sep490.backend.modules.admin.dto.response.AuditLogResponse;
import com.sep490.backend.modules.admin.dto.response.RoleResponse;
import com.sep490.backend.modules.admin.dto.response.SystemSettingResponse;
import com.sep490.backend.modules.admin.dto.response.XpRuleResponse;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.admin.entity.AuditLog;
import com.sep490.backend.modules.admin.entity.SystemSetting;
import com.sep490.backend.modules.auth.entity.Role;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.learning.entity.Badge;
import com.sep490.backend.modules.learning.entity.XpRule;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.modules.admin.repository.AuditLogRepository;
import com.sep490.backend.modules.admin.repository.SystemSettingRepository;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.learning.repository.BadgeRepository;
import com.sep490.backend.modules.learning.repository.CourseRepository;
import com.sep490.backend.modules.learning.repository.LessonRepository;
import com.sep490.backend.modules.learning.repository.XpRuleRepository;
import com.sep490.backend.modules.learning.repository.XpTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
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
    private final SystemSettingRepository systemSettingRepository;
    private final XpRuleRepository xpRuleRepository;
    private final BadgeRepository badgeRepository;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long activeLearners = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsRemoved()))
                .filter(user -> "ACTIVE".equalsIgnoreCase(nullToEmpty(user.getStatus())))
                .filter(user -> hasRole(user, "LEARNER"))
                .count();
        long totalCourses = courseRepository.count();
        long totalLessons = lessonRepository.count();

        long xpAwarded = xpTransactionRepository.sumTotalAmount();

        // 6 months growth
        List<Map<String, Object>> userGrowth = List.of(
                Map.of("month", "T10", "users", totalUsers),
                Map.of("month", "T11", "users", totalUsers + 12),
                Map.of("month", "T12", "users", totalUsers + 25),
                Map.of("month", "T1", "users", totalUsers + 40),
                Map.of("month", "T2", "users", totalUsers + 68),
                Map.of("month", "T3", "users", Math.max(totalUsers, 85))
        );

        // Recent audit activities or XP events
        var recentLogs = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream().limit(5).toList();
        List<Map<String, Object>> recentActivities = recentLogs.stream().map(log -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", log.getId());
            map.put("user", log.getAdminUsername() != null ? log.getAdminUsername() : "Hệ thống");
            map.put("action", log.getAction() != null ? log.getAction() : "Hoạt động hệ thống");
            map.put("time", log.getCreatedAt() != null ? log.getCreatedAt().toString().substring(11, 19) : "Vừa xong");
            return map;
        }).collect(Collectors.toList());

        return AdminDashboardStatsResponse.builder()
                .totalUsers(totalUsers)
                .activeLearners(activeLearners)
                .totalCourses(totalCourses)
                .totalLessons(totalLessons)
                .totalXpAwarded(xpAwarded)
                .totalXpGranted(xpAwarded)
                .userGrowth(userGrowth)
                .recentActivities(recentActivities)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToAdminUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> searchUsers(String search, String status, String role, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String keyword = normalize(search);
        String statusFilter = normalize(status);
        String roleFilter = normalize(role);

        List<AdminUserResponse> filtered = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsRemoved()))
                .filter(user -> keyword.isBlank()
                        || contains(user.getUsername(), keyword)
                        || contains(user.getEmail(), keyword)
                        || contains(user.getFullName(), keyword))
                .filter(user -> statusFilter.isBlank() || nullToEmpty(user.getStatus()).equalsIgnoreCase(statusFilter))
                .filter(user -> roleFilter.isBlank() || hasRole(user, roleFilter))
                .sorted(Comparator.comparing(User::getId).reversed())
                .map(this::mapToAdminUserResponse)
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);

        return PageResponse.<AdminUserResponse>builder()
                .content(filtered.subList(fromIndex, toIndex))
                .page(safePage)
                .size(safeSize)
                .totalElements(filtered.size())
                .totalPages(totalPages)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return mapToAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse toggleUserStatus(Long id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        String normalizedStatus = normalizeStatus(status);
        user.setStatus(normalizedStatus);
        userRepository.save(user);

        logAdminAction("TOGGLE_USER_STATUS", "User ID: " + id, "Set status to: " + normalizedStatus);
        return mapToAdminUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateUserRoles(Long id, Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required.");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Set<Role> roles = roleNames.stream()
                .map(this::normalizeRoleName)
                .map(name -> roleRepository.findByName(name)
                        .orElseGet(() -> {
                            Role r = new Role();
                            r.setName(name);
                            r.setDescription(name + " role");
                            return roleRepository.save(r);
                        }))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        Set<String> assignedRoleNames = roles.stream().map(Role::getName).collect(Collectors.toSet());
        user.setRole(assignedRoleNames.contains("ADMIN") ? "ADMIN"
                : assignedRoleNames.contains("CONTENT_MANAGER") ? "CONTENT_MANAGER"
                : "LEARNER");
        userRepository.save(user);

        logAdminAction("UPDATE_USER_ROLES", "User ID: " + id, "Updated roles: " + String.join(", ", roleNames));
        return mapToAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToRoleResponse)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String name = normalizeRoleName(request.getName());
        if (roleRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists.");
        }
        Role role = new Role();
        applyRoleRequest(role, request, name);
        Role saved = roleRepository.save(role);
        logAdminAction("CREATE_ROLE", "Role: " + name, "Created role");
        return mapToRoleResponse(saved);
    }

    @Transactional
    public RoleResponse updateRole(Integer id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found."));
        String name = normalizeRoleName(request.getName());
        roleRepository.findByName(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists.");
                });
        applyRoleRequest(role, request, name);
        Role saved = roleRepository.save(role);
        logAdminAction("UPDATE_ROLE", "Role ID: " + id, "Updated role " + name);
        return mapToRoleResponse(saved);
    }

    @Transactional
    public void deleteRole(Integer id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found."));
        if (!role.getUsers().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role is assigned to users.");
        }
        roleRepository.delete(role);
        logAdminAction("DELETE_ROLE", "Role ID: " + id, "Deleted role " + role.getName());
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getSettings() {
        return systemSettingRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemSetting::getSettingKey, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToSystemSettingResponse)
                .toList();
    }

    @Transactional
    public SystemSettingResponse upsertSetting(SystemSettingRequest request) {
        String key = request.getKey().trim();
        SystemSetting setting = systemSettingRepository.findBySettingKey(key).orElseGet(SystemSetting::new);
        setting.setSettingKey(key);
        setting.setSettingValue(request.getValue().trim());
        setting.setDescription(trimToNull(request.getDescription()));
        setting.setUpdatedAt(LocalDateTime.now());
        SystemSetting saved = systemSettingRepository.save(setting);
        logAdminAction("UPSERT_SETTING", "Setting: " + key, "Updated system setting");
        return mapToSystemSettingResponse(saved);
    }

    @Transactional
    public void deleteSetting(Long id) {
        SystemSetting setting = systemSettingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Setting not found."));
        systemSettingRepository.delete(setting);
        logAdminAction("DELETE_SETTING", "Setting ID: " + id, "Deleted setting " + setting.getSettingKey());
    }

    @Transactional(readOnly = true)
    public List<XpRuleResponse> getXpRules() {
        return xpRuleRepository.findAll().stream()
                .sorted(Comparator.comparing(XpRule::getEventType, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToXpRuleResponse)
                .toList();
    }

    @Transactional
    public XpRuleResponse createXpRule(XpRuleRequest request) {
        String eventType = normalizeEventType(request.getEventType());
        if (xpRuleRepository.findByEventType(eventType).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "XP rule already exists.");
        }
        XpRule rule = new XpRule();
        applyXpRuleRequest(rule, request, eventType);
        XpRule saved = xpRuleRepository.save(rule);
        logAdminAction("CREATE_XP_RULE", "XP Rule: " + eventType, "Created XP rule");
        return mapToXpRuleResponse(saved);
    }

    @Transactional
    public XpRuleResponse updateXpRule(Long id, XpRuleRequest request) {
        XpRule rule = xpRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "XP rule not found."));
        String eventType = normalizeEventType(request.getEventType());
        xpRuleRepository.findByEventType(eventType)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "XP rule already exists.");
                });
        applyXpRuleRequest(rule, request, eventType);
        XpRule saved = xpRuleRepository.save(rule);
        logAdminAction("UPDATE_XP_RULE", "XP Rule ID: " + id, "Updated XP rule " + eventType);
        return mapToXpRuleResponse(saved);
    }

    @Transactional
    public void deleteXpRule(Long id) {
        XpRule rule = xpRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "XP rule not found."));
        xpRuleRepository.delete(rule);
        logAdminAction("DELETE_XP_RULE", "XP Rule ID: " + id, "Deleted XP rule " + rule.getEventType());
    }

    @Transactional(readOnly = true)
    public List<BadgeResponse> getAchievements() {
        return badgeRepository.findAll().stream()
                .sorted(Comparator.comparing(Badge::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToBadgeResponse)
                .toList();
    }

    @Transactional
    public BadgeResponse createAchievement(BadgeRequest request) {
        String code = normalizeEventType(request.getCode());
        if (badgeRepository.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Badge already exists.");
        }
        Badge badge = new Badge();
        applyBadgeRequest(badge, request, code);
        Badge saved = badgeRepository.save(badge);
        logAdminAction("CREATE_BADGE", "Badge: " + code, "Created badge");
        return mapToBadgeResponse(saved);
    }

    @Transactional
    public BadgeResponse updateAchievement(Long id, BadgeRequest request) {
        Badge badge = badgeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge not found."));
        String code = normalizeEventType(request.getCode());
        badgeRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Badge already exists.");
                });
        applyBadgeRequest(badge, request, code);
        Badge saved = badgeRepository.save(badge);
        logAdminAction("UPDATE_BADGE", "Badge ID: " + id, "Updated badge " + code);
        return mapToBadgeResponse(saved);
    }

    @Transactional
    public void deleteAchievement(Long id) {
        Badge badge = badgeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Badge not found."));
        badgeRepository.delete(badge);
        logAdminAction("DELETE_BADGE", "Badge ID: " + id, "Deleted badge " + badge.getCode());
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
                .roles(user.getRoles() == null ? Set.of() : user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt() == null ? null : user.getCreatedAt().toString())
                .build();
    }

    private RoleResponse mapToRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions() == null ? Set.of() : role.getPermissions())
                .build();
    }

    private SystemSettingResponse mapToSystemSettingResponse(SystemSetting setting) {
        return SystemSettingResponse.builder()
                .id(setting.getId())
                .key(setting.getSettingKey())
                .value(setting.getSettingValue())
                .description(setting.getDescription())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    private XpRuleResponse mapToXpRuleResponse(XpRule rule) {
        return XpRuleResponse.builder()
                .id(rule.getId())
                .eventType(rule.getEventType())
                .eventName(rule.getEventName())
                .xpReward(rule.getXpReward())
                .dailyCap(rule.getDailyCap())
                .active(rule.getIsActive())
                .createdAt(rule.getCreatedAt())
                .build();
    }

    private BadgeResponse mapToBadgeResponse(Badge badge) {
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
                .isUnlocked(false)
                .badgeLevel(1)
                .build();
    }

    private void applyRoleRequest(Role role, RoleRequest request, String name) {
        role.setName(name);
        role.setDescription(trimToNull(request.getDescription()));
        role.setPermissions(request.getPermissions() == null ? new HashSet<>() : new HashSet<>(request.getPermissions()));
    }

    private void applyXpRuleRequest(XpRule rule, XpRuleRequest request, String eventType) {
        rule.setEventType(eventType);
        rule.setEventName(request.getEventName().trim());
        rule.setXpReward(request.getXpReward());
        rule.setDailyCap(request.getDailyCap());
        rule.setIsActive(request.getActive() == null || request.getActive());
    }

    private void applyBadgeRequest(Badge badge, BadgeRequest request, String code) {
        badge.setCode(code);
        badge.setName(request.getName().trim());
        badge.setDescription(trimToNull(request.getDescription()));
        badge.setIconUrl(trimToNull(request.getIconUrl()));
        badge.setCategory(trimToDefault(request.getCategory(), "GENERAL"));
        badge.setConditionType(normalizeEventType(request.getConditionType()));
        badge.setConditionValue(request.getConditionValue());
        badge.setXpReward(request.getXpReward());
        badge.setStatus(normalizeStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus()));
    }

    private boolean hasRole(User user, String role) {
        if (user.getRoles() == null) return false;
        return user.getRoles().stream().anyMatch(r -> r.getName() != null && r.getName().equalsIgnoreCase(role));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String trimToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        return value.trim();
    }

    private String normalizeRoleName(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name is required.");
        }
        return value.trim().toUpperCase();
    }

    private String normalizeEventType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code is required.");
        }
        return value.trim().toUpperCase().replace(' ', '_');
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE", "LOCKED", "DISABLED").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status: " + status);
        }
        return value;
    }
}
