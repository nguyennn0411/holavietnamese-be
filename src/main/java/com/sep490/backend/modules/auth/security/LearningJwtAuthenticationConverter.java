package com.sep490.backend.modules.auth.security;
import com.sep490.backend.common.security.LearnerPrincipal;

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

/** Resolve both JWT and legacy session authentication to the same database user ID. */
@Component
@RequiredArgsConstructor
public class LearningJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken>, UserDetailsService {
    private final UserRepository users;
    @Override public LearnerPrincipal loadUserByUsername(String identifier) {
        var user=users.findActiveByUsernameWithRoles(identifier)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        if (!user.isEnabled() || !"ACTIVE".equals(user.getStatus())) throw new DisabledException("Account is disabled.");
        var roleNames = new LinkedHashSet<String>();
        var permissions = new LinkedHashSet<String>();
        user.getRoles().forEach(role -> {
            roleNames.add(role.getName());
            if (role.getPermissions() != null) permissions.addAll(role.getPermissions());
        });
        if (roleNames.isEmpty() && user.getRole() != null) roleNames.add(user.getRole());
        boolean admin=roleNames.contains("ADMIN");
        boolean contentManager=roleNames.contains("CONTENT_MANAGER");
        String effectiveRole=admin?"ADMIN":contentManager?"CONTENT_MANAGER":"LEARNER";
        return new LearnerPrincipal(user.getId(),user.getEmail(),user.getPasswordHash(),true,effectiveRole,roleNames,permissions);
    }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        var principal=loadUserByUsername(jwt.getSubject());
        return new UsernamePasswordAuthenticationToken(principal,jwt.getTokenValue(),principal.getAuthorities());
    }
}
