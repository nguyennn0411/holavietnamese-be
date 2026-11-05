package com.sep490.backend.modules.admin.service;

import com.sep490.backend.common.audit.AuditLogPort;
import com.sep490.backend.modules.admin.entity.AuditLog;
import com.sep490.backend.modules.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService implements AuditLogPort {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public void record(String action, String target, String details, String ipAddress) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "system";

        AuditLog auditLog = new AuditLog();
        auditLog.setAdminUsername(limit(actor, 100));
        auditLog.setAction(limit(action, 100));
        auditLog.setTarget(limit(target, 200));
        auditLog.setDetails(details);
        auditLog.setIpAddress(limit(ipAddress, 50));
        auditLogRepository.save(auditLog);
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
