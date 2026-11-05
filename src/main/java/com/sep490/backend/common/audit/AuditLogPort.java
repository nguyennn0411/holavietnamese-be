package com.sep490.backend.common.audit;

/** Records security-relevant administrative changes without coupling feature modules to admin. */
public interface AuditLogPort {
    void record(String action, String target, String details, String ipAddress);
}
