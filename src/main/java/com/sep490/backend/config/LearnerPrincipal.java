package com.sep490.backend.config;

import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
public record LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role) implements UserDetails {
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled) {
        this(id, email, passwordHash, enabled, "LEARNER");
    }
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return "ADMIN".equals(role)
            ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_LEARNER"))
            : List.of(new SimpleGrantedAuthority("ROLE_LEARNER"));
    }
    public String getPassword() { return passwordHash; }
    public String getUsername() { return email; }
    public boolean isEnabled() { return enabled; }
}
