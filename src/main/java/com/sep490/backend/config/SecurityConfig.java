package com.sep490.backend.config;

import com.sep490.backend.repository.jpa.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/auth/token",
            "/api/auth/google",
            "/api/auth/register",
            "/api/auth/introspect",
            "/api/auth/logout",
            "/api/auth/refresh",
            "/api/auth/verify-email/resend",
            "/api/auth/forgot-password/initiate",
            "/api/auth/forgot-password/verify-otp",
            "/api/auth/forgot-password/reset-password",
            "/api/users/register",
            "/api/login",
    };

    private final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/auth/verify-email",
            "/api/lessons/public/**",
            "/api/topics/public/**",
            "/api/courses/**",
            "/api/lessons/**",
            "/api/vocabulary/**",
            "/api/csrf",
            "/error",
    };

    @Autowired
    private CustomJwtDecoder customJwtDecoder;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                );

        httpSecurity.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwtConfigurer -> jwtConfigurer
                        .decoder(customJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

        httpSecurity.formLogin(form -> form
                .loginProcessingUrl("/api/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> response.setStatus(401)));

        httpSecurity.logout(logout -> logout
                .logoutUrl("/api/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));

        httpSecurity.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/**", "/api/users/register"));

        return httpSecurity.build();
    }

    @Bean
    public UserDetailsService userDetailsService(UserJpaRepository users) {
        return username -> users.findByEmail(username)
                .map(user -> new LearnerPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getPasswordHash(),
                        user.isEnabled(),
                        user.getRole()))
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://holavietnamese.vercel.app",
                "https://holavietnamese-*.vercel.app"
        ));
        corsConfiguration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Accept", "X-Requested-With", "X-CSRF-TOKEN"
        ));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
