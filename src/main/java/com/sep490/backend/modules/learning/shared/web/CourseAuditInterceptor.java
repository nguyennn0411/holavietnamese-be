package com.sep490.backend.modules.learning.shared.web;

import com.sep490.backend.common.audit.AuditLogPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseAuditInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AuditLogPort auditLog;

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        if (exception != null || response.getStatus() >= 400 || !WRITE_METHODS.contains(request.getMethod())) {
            return;
        }

        String path = request.getRequestURI();
        String action = actionFor(request.getMethod(), path);
        if (action == null) return;

        try {
            auditLog.record(
                    action,
                    path,
                    request.getMethod() + " " + path + " completed with HTTP " + response.getStatus(),
                    request.getRemoteAddr());
        } catch (RuntimeException auditFailure) {
            // The business operation has already completed. Keep its response stable and surface
            // the audit persistence failure in server logs for operations monitoring.
            log.error("Unable to record course audit event {} for {}", action, path, auditFailure);
        }
    }

    static String actionFor(String method, String path) {
        if ("POST".equals(method) && "/api/admin/courses".equals(path)) return "CREATE_COURSE";
        if ("PUT".equals(method) && path.matches("/api/admin/courses/\\d+")) return "UPDATE_COURSE";
        if ("PATCH".equals(method) && path.matches("/api/admin/courses/\\d+/status")) return "CHANGE_COURSE_STATUS";
        if ("POST".equals(method) && path.matches("/api/admin/courses/\\d+/submit")) return "SUBMIT_COURSE";
        if ("POST".equals(method) && path.matches("/api/admin/courses/\\d+/review")) return "REVIEW_COURSE";
        if ("POST".equals(method) && path.matches("/api/admin/courses/\\d+/edit-request")) return "REQUEST_COURSE_EDIT";
        if ("POST".equals(method) && path.matches("/api/admin/courses/\\d+/edit-request/review")) return "REVIEW_COURSE_EDIT_REQUEST";
        if ("PATCH".equals(method) && path.matches("/api/admin/courses/\\d+/final-assessment")) return "SET_COURSE_FINAL_ASSESSMENT";
        if ("DELETE".equals(method) && path.matches("/api/admin/courses/\\d+")) return "DELETE_OR_ARCHIVE_COURSE";
        if ("POST".equals(method) && "/api/admin/courses/import".equals(path)) return "IMPORT_COURSE";
        if ("POST".equals(method) && "/api/admin/courses/workbook/import".equals(path)) return "IMPORT_COURSE_WORKBOOK";

        if ("POST".equals(method) && path.matches("/api/admin/courses/\\d+/modules")) return "CREATE_COURSE_MODULE";
        if ("PUT".equals(method) && path.matches("/api/admin/courses/\\d+/modules/reorder")) return "REORDER_COURSE_MODULES";
        if ("PUT".equals(method) && path.matches("/api/admin/modules/\\d+")) return "UPDATE_COURSE_MODULE";
        if ("DELETE".equals(method) && path.matches("/api/admin/modules/\\d+")) return "DELETE_COURSE_MODULE";

        if ("POST".equals(method) && path.matches("/api/admin/modules/\\d+/lessons")) return "CREATE_COURSE_LESSON";
        if ("PUT".equals(method) && path.matches("/api/admin/modules/\\d+/lessons/reorder")) return "REORDER_COURSE_LESSONS";
        if ("PUT".equals(method) && path.matches("/api/admin/lessons/\\d+")) return "UPDATE_COURSE_LESSON";
        if ("PATCH".equals(method) && path.matches("/api/admin/lessons/\\d+/status")) return "CHANGE_COURSE_LESSON_STATUS";
        if ("DELETE".equals(method) && path.matches("/api/admin/lessons/\\d+")) return "DELETE_COURSE_LESSON";

        if ("POST".equals(method) && path.matches("/api/admin/lessons/\\d+/activities")) return "CREATE_COURSE_ACTIVITY";
        if ("PUT".equals(method) && path.matches("/api/admin/lessons/\\d+/activities/reorder")) return "REORDER_COURSE_ACTIVITIES";
        if ("PUT".equals(method) && path.matches("/api/admin/activities/\\d+")) return "UPDATE_COURSE_ACTIVITY";
        if ("DELETE".equals(method) && path.matches("/api/admin/activities/\\d+")) return "DELETE_COURSE_ACTIVITY";

        if ("POST".equals(method) && "/api/admin/quizzes".equals(path)) return "CREATE_COURSE_QUIZ";
        if ("PUT".equals(method) && path.matches("/api/admin/quizzes/\\d+")) return "UPDATE_COURSE_QUIZ";
        if ("PATCH".equals(method) && path.matches("/api/admin/quizzes/\\d+/status")) return "CHANGE_COURSE_QUIZ_STATUS";
        if ("DELETE".equals(method) && path.matches("/api/admin/quizzes/\\d+")) return "DELETE_COURSE_QUIZ";
        if ("POST".equals(method) && path.matches("/api/admin/quizzes/\\d+/questions")) return "ADD_COURSE_QUIZ_QUESTION";
        if ("DELETE".equals(method) && path.matches("/api/admin/quizzes/\\d+/questions/\\d+")) return "REMOVE_COURSE_QUIZ_QUESTION";
        if ("PUT".equals(method) && path.matches("/api/admin/quizzes/\\d+/questions/reorder")) return "REORDER_COURSE_QUIZ_QUESTIONS";

        if ("POST".equals(method) && "/api/admin/questions".equals(path)) return "CREATE_COURSE_QUESTION";
        if ("PUT".equals(method) && path.matches("/api/admin/questions/\\d+")) return "UPDATE_COURSE_QUESTION";
        if ("POST".equals(method) && path.matches("/api/admin/questions/\\d+/versions")) return "VERSION_COURSE_QUESTION";
        return null;
    }
}
