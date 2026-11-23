package com.sep490.backend.modules.learning.shared.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class CourseAuditWebConfig implements WebMvcConfigurer {

    private final CourseAuditInterceptor courseAuditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(courseAuditInterceptor)
                .addPathPatterns(
                        "/api/admin/courses", "/api/admin/courses/**",
                        "/api/admin/modules/**", "/api/admin/lessons/**",
                        "/api/admin/activities/**", "/api/admin/quizzes",
                        "/api/admin/quizzes/**", "/api/admin/questions",
                        "/api/admin/questions/**",
                        "/api/content/courses", "/api/content/courses/**",
                        "/api/content/modules/**", "/api/content/lessons/**",
                        "/api/content/activities/**", "/api/content/quizzes",
                        "/api/content/quizzes/**", "/api/content/questions",
                        "/api/content/questions/**");
    }
}
