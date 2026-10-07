package com.sep490.backend.config;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, CustomJwtDecoder decoder,
            LearningJwtAuthenticationConverter converter) throws Exception {
        return http.cors(c -> {}).authorizeHttpRequests(a -> a
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/auth/**", "/api/users/register", "/api/login", "/error",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers(HttpMethod.GET, "/media/foundation/**", "/api/csrf", "/api/courses", "/api/courses/{id}",
                "/api/grammar", "/api/grammar/{id}", "/api/lessons/public/**", "/api/topics/public/**").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
            // JWT clients send an explicit Authorization header; legacy session clients keep CSRF.
            .csrf(c -> c.ignoringRequestMatchers("/api/auth/**", "/api/users/register")
                .ignoringRequestMatchers(r -> r.getHeader("Authorization") != null
                    && r.getHeader("Authorization").startsWith("Bearer ")))
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
            .formLogin(f -> f.loginProcessingUrl("/api/login")
                .successHandler((req,res,auth) -> res.setStatus(204))
                .failureHandler((req,res,ex) -> error(res,401,"Invalid email or password.")))
            .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req,res,ex) -> error(res,401,"Please sign in to continue."))
                .accessDeniedHandler((req,res,ex) -> error(res,403,"Access denied or session expired.")))
            .build();
    }
    private static void error(jakarta.servlet.http.HttpServletResponse response,int status,String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json");
        response.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\"}");
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origin:http://localhost:5173}") String origin) {
        CorsConfiguration c=new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of(origin,"http://localhost:5173","http://127.0.0.1:5173",
            "http://localhost:3000","https://holavietnamese.vercel.app","https://holavietnamese-*.vercel.app"));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","X-Requested-With","X-CSRF-TOKEN"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",c);return source;
    }
}
