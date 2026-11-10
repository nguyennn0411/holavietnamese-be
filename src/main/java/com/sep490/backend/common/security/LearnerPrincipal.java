package com.sep490.backend.common.security;

import com.sep490.backend.modules.auth.entity.AccountRole;

import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
public record LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role,
        Set<String> permissions) implements UserDetails {
    public LearnerPrincipal {
        role = AccountRole.normalizeStored(role);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role,
            Set<String> roles, Set<String> permissions) {
        this(id, email, passwordHash, enabled, (roles != null && !roles.isEmpty()) ? roles.iterator().next() : role, permissions);
    }
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled, String role) {
        this(id, email, passwordHash, enabled, role, Set.of());
    }
    public LearnerPrincipal(Integer id, String email, String passwordHash, boolean enabled) {
        this(id == null ? null : id.longValue(), email, passwordHash, enabled, "LEARNER");
    }
    public LearnerPrincipal(Long id, String email, String passwordHash, boolean enabled) {
        this(id, email, passwordHash, enabled, "LEARNER");
    }
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        permissions.forEach(value -> authorities.add(new SimpleGrantedAuthority(value)));
        return List.copyOf(authorities);
    }
    public String getPassword() { return passwordHash; }
    public String getUsername() { return email; }
    public boolean isEnabled() { return enabled; }
}
