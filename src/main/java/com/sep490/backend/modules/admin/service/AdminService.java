package com.sep490.backend.modules.admin.service;
import com.sep490.backend.common.audit.AuditLogPort;

import com.sep490.backend.common.dto.PageResponse;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.admin.dto.request.BadgeRequest;
import com.sep490.backend.modules.admin.dto.request.SystemSettingRequest;
import com.sep490.backend.modules.admin.dto.request.XpRuleRequest;
import com.sep490.backend.modules.admin.dto.response.AdminDashboardStatsResponse;
import com.sep490.backend.modules.admin.dto.response.AdminUserResponse;
import com.sep490.backend.modules.admin.dto.response.AuditLogResponse;
import com.sep490.backend.modules.admin.dto.response.RoleResponse;
import com.sep490.backend.modules.admin.dto.response.SystemSettingResponse;
import com.sep490.backend.modules.admin.dto.response.XpRuleResponse;
import com.sep490.backend.modules.admin.entity.SystemSetting;
import com.sep490.backend.modules.admin.repository.AuditLogRepository;
import com.sep490.backend.modules.admin.repository.SystemSettingRepository;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.entity.AccountRole;
import com.sep490.backend.modules.auth.entity.Role;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import com.sep490.backend.modules.auth.service.AuthenticationService;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.BadgeRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpRuleRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.XpTransactionRepository;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.Badge;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.XpRule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final Set<String> FIXED_ROLES = Set.of(
            AccountRole.LEARNER.name(), AccountRole.CONTENT.name(), AccountRole.ADMIN.name());

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final XpTransactionRepository xpTransactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogPort auditLogPort;
    private final SystemSettingRepository systemSettingRepository;
    private final XpRuleRepository xpRuleRepository;
    private final BadgeRepository badgeRepository;
    private final NotificationPort notifications;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        long totalUsers = userRepository.count();
        long activeLearners = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsRemoved()))
                .filter(user -> "ACTIVE".equalsIgnoreCase(nullToEmpty(user.getStatus())))
                .filter(user -> hasRole(user, AccountRole.LEARNER.name()))
                .count();
        long totalCourses = courseRepository.count();
        long totalLessons = lessonRepository.count();
        long xpAwarded = xpTransactionRepository.sumTotalAmount();

        YearMonth currentYm = YearMonth.now();
        List<User> allUsers = userRepository.findAll().stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsRemoved()))
                .toList();

        List<Map<String, Object>> userGrowth = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = currentYm.minusMonths(i);
            LocalDateTime endOfMonth = ym.atEndOfMonth().atTime(23, 59, 59);
            long count = allUsers.stream()
                    .filter(u -> u.getCreatedAt() == null || !u.getCreatedAt().isAfter(endOfMonth))
                    .count();
            String monthLabel = "T" + ym.getMonthValue();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", monthLabel);
            point.put("users", count);
            userGrowth.add(point);
        }

        List<Map<String, Object>> recentActivities = new ArrayList<>(
                auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                        .limit(5)
                        .map(log -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("id", log.getId());
                            result.put("user", log.getAdminUsername() == null ? "Hệ thống" : log.getAdminUsername());
                            result.put("action", log.getAction() == null ? "Thao tác quản trị" : log.getAction());
                            result.put("target", log.getTarget() == null ? "" : log.getTarget());
                            result.put("details", log.getDetails() == null ? "" : log.getDetails());
                            result.put("time", log.getCreatedAt() == null ? LocalDateTime.now().toString() : log.getCreatedAt().toString());
                            return result;
                        })
                        .toList()
        );

        if (recentActivities.isEmpty()) {
            allUsers.stream()
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .limit(5)
                    .forEach(u -> {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("id", u.getId());
                        result.put("user", u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername());
                        result.put("action", "Đăng ký tài khoản mới (" + u.getRole() + ")");
                        result.put("target", u.getEmail());
                        result.put("details", "Tài khoản " + u.getStatus());
                        result.put("time", u.getCreatedAt() != null ? u.getCreatedAt().toString() : LocalDateTime.now().toString());
                        recentActivities.add(result);
                    });
        }

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
        return mapToAdminUserResponse(findUser(id));
    }

    @Transactional
    public AdminUserResponse toggleUserStatus(Long id, String status) {
        User user = findUser(id);
        String previousStatus = user.getStatus();
        String normalizedStatus = normalizeStatus(status);
        user.setStatus(normalizedStatus);
        userRepository.save(user);

        if (!normalizedStatus.equalsIgnoreCase(nullToEmpty(previousStatus))) {
            notifications.send(user.getId(), NotificationMessage.mandatory(
                    "Account status updated",
                    "An administrator changed your account status to " + normalizedStatus + ".",
                    NotificationType.SECURITY,
                    "/settings"));
        }
        logAdminAction("TOGGLE_USER_STATUS", "User ID: " + id, "Set status to: " + normalizedStatus);
        return mapToAdminUserResponse(user);
    }

    /**
     * The only application-managed role transition. The target user may only
     * move between LEARNER and CONTENT; ADMIN identities are database-managed.
     */
    @Transactional
    public AdminUserResponse updateUserRole(Long id, String requestedRole) {
        requireAdministrator();
        User user = findUser(id);
        String currentRole = AccountRole.normalizeStored(user.getRole());
        if (AccountRole.ADMIN.name().equals(currentRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The role of an ADMIN account cannot be changed through the application.");
        }

        if (!AccountRole.isAssignableByAdmin(requestedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only LEARNER or CONTENT may be assigned through the application.");
        }

        user.setRole(requestedRole);
        userRepository.save(user);

        // JWTs issued before this instant are rejected; session authorities are
        // also reloaded on every request by CurrentUserRoleFilter.
        authenticationService.invalidateUserSessions(user.getUsername());
        authenticationService.invalidateUserSessions(user.getEmail());

        notifications.send(user.getId(), NotificationMessage.mandatory(
                "Account role updated",
                "Your current role is " + requestedRole + ".",
                NotificationType.SECURITY,
                "/settings"));
        logAdminAction("UPDATE_USER_ROLE", "User ID: " + id, "Updated role to: " + requestedRole);
        return mapToAdminUserResponse(user);
    }

    /**
     * Role records are fixed system metadata. They are readable but are not
     * creatable, editable, or deletable through the application API.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .filter(role -> FIXED_ROLES.contains(role.getName()))
                .sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToRoleResponse)
                .toList();
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

    @Transactional
    public List<XpRuleResponse> getXpRules() {
        if (xpRuleRepository.count() == 0) {
            seedDefaultXpRules();
        }
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

    @Transactional
    public List<BadgeResponse> getAchievements() {
        if (badgeRepository.count() == 0) {
            seedDefaultBadges();
        }
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
                        .status("SUCCESS")
                        .build())
                .collect(Collectors.toList());
    }

    public void logAdminAction(String action, String target, String details) {
        auditLogPort.record(action, target, details, null);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private void requireAdministrator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator permission is required.");
        }
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
                .role(AccountRole.normalizeStored(user.getRole()))
                .createdAt(user.getCreatedAt() == null ? null : user.getCreatedAt().toString())
                .build();
    }

    private RoleResponse mapToRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions() == null ? Set.of() : Set.copyOf(role.getPermissions()))
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
        String status = badge.getStatus() != null ? badge.getStatus() : "ACTIVE";
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
                .status(status)
                .active("ACTIVE".equalsIgnoreCase(status))
                .isUnlocked(false)
                .badgeLevel(1)
                .build();
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
        String resolvedStatus = request.getStatus();
        if (resolvedStatus == null && request.getActive() != null) {
            resolvedStatus = request.getActive() ? "ACTIVE" : "INACTIVE";
        }
        badge.setStatus(normalizeStatus(resolvedStatus == null ? "ACTIVE" : resolvedStatus));
    }

    private boolean hasRole(User user, String role) {
        return AccountRole.normalizeStored(user.getRole()).equals(role == null ? "" : role.trim().toUpperCase(Locale.ROOT));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String normalizeEventType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE", "LOCKED", "DISABLED").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status: " + status);
        }
        return value;
    }

    private void seedDefaultXpRules() {
        createXpRuleIfAbsent("LESSON_COMPLETE", "Hoàn thành 1 bài học", 20, 200);
        createXpRuleIfAbsent("DAILY_LOGIN", "Đăng nhập mỗi ngày", 10, 10);
        createXpRuleIfAbsent("DAILY_CHECKIN", "Điểm danh hàng ngày", 10, 10);
        createXpRuleIfAbsent("QUIZ_PASS", "Đạt bài kiểm tra", 15, 150);
        createXpRuleIfAbsent("VOCAB_LEARNED", "Học 1 từ mới", 5, 50);
        createXpRuleIfAbsent("AI_ROLEPLAY_COMPLETE", "Hoàn thành hội thoại AI", 25, 100);
        createXpRuleIfAbsent("STREAK_MAINTAIN", "Duy trì chuỗi học tập", 30, 30);
    }

    private void createXpRuleIfAbsent(String eventType, String eventName, int xpReward, int dailyCap) {
        if (xpRuleRepository.findByEventType(eventType).isEmpty()) {
            XpRule rule = new XpRule();
            rule.setEventType(eventType);
            rule.setEventName(eventName);
            rule.setXpReward(xpReward);
            rule.setDailyCap(dailyCap);
            rule.setIsActive(true);
            xpRuleRepository.save(rule);
        }
    }

    private void seedDefaultBadges() {
        createBadgeIfAbsent("FIRST_LESSON", "Bước Đầu Tiên", "Hoàn thành bài học đầu tiên của bạn", "/badges/first_lesson.png", "LESSON", "LESSON_COMPLETED", 1, 50);
        createBadgeIfAbsent("STREAK_7_DAYS", "Thói Quen Vàng", "Duy trì chuỗi 7 ngày học liên tục", "/badges/streak_7.png", "STREAK", "STREAK_DAYS", 7, 100);
        createBadgeIfAbsent("VOCAB_50", "Nhà Sưu Tầm Từ Vựng", "Học và lưu 50 từ vựng vào sổ tay", "/badges/vocab_50.png", "VOCABULARY", "VOCAB_LEARNED", 50, 150);
        createBadgeIfAbsent("QUIZ_MASTER", "Bậc Thầy Quiz", "Hoàn thành 10 bài kiểm tra đạt điểm tối đa", "/badges/quiz_master.png", "QUIZ", "QUIZ_PERFECT", 10, 200);
        createBadgeIfAbsent("AI_PIONEER", "Nhà Giao Tiếp AI", "Hoàn thành 5 lượt giao tiếp AI", "/badges/ai_pioneer.png", "AI_TUTOR", "AI_ROLEPLAY_COMPLETED", 5, 120);
    }

    private void createBadgeIfAbsent(String code, String name, String desc, String iconUrl, String category, String conditionType, int conditionValue, int xpReward) {
        if (badgeRepository.findByCode(code).isEmpty()) {
            Badge badge = new Badge();
            badge.setCode(code);
            badge.setName(name);
            badge.setDescription(desc);
            badge.setIconUrl(iconUrl);
            badge.setCategory(category);
            badge.setConditionType(conditionType);
            badge.setConditionValue(conditionValue);
            badge.setXpReward(xpReward);
            badge.setStatus("ACTIVE");
            badgeRepository.save(badge);
        }
    }
}
