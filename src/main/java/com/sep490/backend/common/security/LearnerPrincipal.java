package com.sep490.backend.common.security;

import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
public record LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role,
        Set<String> roles, Set<String> permissions) implements UserDetails {
    public LearnerPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role) {
        this(id, email, passwordHash, enabled, role, Set.of(role), Set.of());
    }
    public LearnerPrincipal(Integer id, String email, String passwordHash, boolean enabled) {
        this(id == null ? null : id.longValue(), email, passwordHash, enabled, "LEARNER");
    }
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled) {
        this(id, email, passwordHash, enabled, "LEARNER");
    }
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        roles.forEach(value -> authorities.add(new SimpleGrantedAuthority(
                value.startsWith("ROLE_") ? value : "ROLE_" + value)));
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        authorities.add(new SimpleGrantedAuthority("ROLE_LEARNER"));
        return List.copyOf(authorities);
    }
    public String getPassword() { return passwordHash; }
    public String getUsername() { return email; }
    public boolean isEnabled() { return enabled; }
}
