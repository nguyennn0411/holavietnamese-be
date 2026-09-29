package com.sep490.backend.config;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import com.sep490.backend.repository.jpa.UserJpaRepository;
@Configuration
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserJpaRepository users) {
        return email -> users.findByEmail(email).map(u -> new LearnerPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.isEnabled(), u.getRole()))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
    @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.cors(c -> {}).authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.GET, "/api/csrf", "/api/courses", "/api/courses/{id}").permitAll()
            .requestMatchers("/api/login", "/error").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().hasRole("LEARNER"))
            .formLogin(f -> f.loginProcessingUrl("/api/login")
                .successHandler((req, res, auth) -> res.setStatus(204))
                .failureHandler((req, res, ex) -> error(res, 401, "Invalid email or password.")))
            .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> error(res, 401, "Please sign in to continue."))
                .accessDeniedHandler((req, res, ex) -> error(res, 403, "Access denied or session expired. Refresh and try again.")))
            .build();
    }
    private static void error(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
    @Bean CorsConfigurationSource cors(@Value("${app.frontend-origin:http://localhost:5173}") String origin) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(List.of(origin)); c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**", c); return source;
    }
}
