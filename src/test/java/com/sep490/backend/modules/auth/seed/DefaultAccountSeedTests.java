package com.sep490.backend.modules.auth.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DefaultAccountSeedTests {

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void createsAdminContentAndLearnerWhenTheyDoNotExist() {
        when(passwordEncoder.encode("123456")).thenReturn("bcrypt-hash");
        var seed = new DefaultAccountSeed(users, passwordEncoder);

        var result = seed.seed();

        var accounts = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.times(3)).save(accounts.capture());
        assertThat(accounts.getAllValues())
                .extracting(User::getUsername, User::getEmail, User::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "admin", "admin@holavietnamese.com", "ADMIN"),
                        org.assertj.core.groups.Tuple.tuple(
                                "content", "content@holavietnamese.com", "CONTENT"),
                        org.assertj.core.groups.Tuple.tuple(
                                "learner", "learner@holavietnamese.com", "LEARNER"));
        assertThat(accounts.getAllValues())
                .allSatisfy(account -> {
                    assertThat(account.getPasswordHash()).isEqualTo("bcrypt-hash");
                    assertThat(account.getStatus()).isEqualTo("ACTIVE");
                    assertThat(account.isEnabled()).isTrue();
                    assertThat(account.getEmailVerified()).isTrue();
                });
        assertThat(result).isEqualTo(new DefaultAccountSeed.SeedResult(3, 0));
    }

    @Test
    void skipsExistingAccountsWithoutChangingThem() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(new User()));
        when(users.findByUsername("content")).thenReturn(Optional.of(new User()));
        when(users.findByUsername("learner")).thenReturn(Optional.of(new User()));
        var seed = new DefaultAccountSeed(users, passwordEncoder);

        var result = seed.seed();

        verify(users, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        assertThat(result).isEqualTo(new DefaultAccountSeed.SeedResult(0, 3));
    }
}
