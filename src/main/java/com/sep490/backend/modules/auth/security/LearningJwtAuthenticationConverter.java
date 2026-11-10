package com.sep490.backend.modules.auth.security;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.auth.entity.AccountRole;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Resolve both JWT and legacy session authentication to the same database user ID. */
@Component
@RequiredArgsConstructor
public class LearningJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken>, UserDetailsService {
    private final UserRepository users;
    private final RoleRepository roles;
    @Override public LearnerPrincipal loadUserByUsername(String identifier) {
        var user=users.findActiveByUsernameWithRoles(identifier)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        if (!user.isEnabled() || !"ACTIVE".equals(user.getStatus())) throw new DisabledException("Account is disabled.");
        String role = AccountRole.normalizeStored(user.getRole());
        Set<String> permissions = roles.findByName(role)
                .map(found -> found.getPermissions() == null ? Set.<String>of() : new LinkedHashSet<>(found.getPermissions()))
                .orElseGet(Set::of);
        return new LearnerPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), true, role, permissions);
    }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        var principal=loadUserByUsername(jwt.getSubject());
        return new UsernamePasswordAuthenticationToken(principal,jwt.getTokenValue(),principal.getAuthorities());
    }
}
