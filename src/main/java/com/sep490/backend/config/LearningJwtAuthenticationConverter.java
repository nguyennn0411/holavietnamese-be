package com.sep490.backend.config;

import com.sep490.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolve both JWT and legacy session authentication to the same database user ID. */
@Component
@RequiredArgsConstructor
public class LearningJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken>, UserDetailsService {
    private final UserRepository users;
    @Override public LearnerPrincipal loadUserByUsername(String identifier) {
        var user=users.findActiveByUsernameWithRoles(identifier)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
        if (!user.isEnabled() || !"ACTIVE".equals(user.getStatus())) throw new DisabledException("Account is disabled.");
        boolean admin=user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getName()))
            || (user.getRoles().isEmpty() && "ADMIN".equals(user.getRole()));
        return new LearnerPrincipal(user.getId(),user.getEmail(),user.getPasswordHash(),true,admin?"ADMIN":"LEARNER");
    }
    @Override public AbstractAuthenticationToken convert(Jwt jwt) {
        var principal=loadUserByUsername(jwt.getSubject());
        return new UsernamePasswordAuthenticationToken(principal,jwt.getTokenValue(),principal.getAuthorities());
    }
}
