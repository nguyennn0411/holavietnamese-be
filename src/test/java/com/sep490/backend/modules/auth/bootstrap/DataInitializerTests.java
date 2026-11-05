package com.sep490.backend.modules.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.modules.auth.entity.Role;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DataInitializerTests {

    @Mock RoleRepository roleRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock Environment environment;

    DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(roleRepository, userRepository, passwordEncoder, environment);
        when(environment.getRequiredProperty("APP_DEMO_PASSWORD")).thenReturn("LongTestPass123");
        when(roleRepository.findByName(anyString())).thenAnswer(invocation ->
                Optional.of(role(invocation.getArgument(0, String.class))));
    }

    @Test
    void mapsAnExistingContentManagerAccountToTheContentRole() {
        User manager = new User();
        manager.setUsername("contentmanager");
        // Hibernate uses field access, so this mirrors a legacy database row.
        ReflectionTestUtils.setField(manager, "role", "CONTENT_MANAGER");
        when(userRepository.findByUsername("contentmanager")).thenReturn(Optional.of(manager));

        initializer.run();

        assertThat(manager.getRole()).isEqualTo("CONTENT");
        verify(userRepository).save(manager);
        verify(passwordEncoder, never()).encode(anyString());
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        role.setPermissions(new HashSet<>(Set.of("TEST_PERMISSION")));
        return role;
    }
}
