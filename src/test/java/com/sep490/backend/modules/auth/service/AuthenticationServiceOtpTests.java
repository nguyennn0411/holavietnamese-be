package com.sep490.backend.modules.auth.service;

import com.sep490.backend.modules.auth.dto.request.AuthenticationRequest;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceOtpTests {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock NotificationPort notifications;

    AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                userRepository, roleRepository, passwordEncoder, emailService, notifications);
    }

    @Test
    void registrationCreatesSixDigitOtpAndReturnsItOnlyWhenMailIsDisabled() {
        User user = pendingUser();
        when(emailService.sendRegistrationVerificationOtp(eq(user.getEmail()), anyString(), eq(10L)))
                .thenReturn(false);
        when(emailService.isConfigured()).thenReturn(false);

        String otp = service.sendRegistrationVerificationOtp(user);

        assertThat(otp).matches("\\d{6}");
        assertThat(user.getEmailVerificationToken()).isEqualTo(otp);
        assertThat(user.getVerificationTokenExpiry()).isAfter(LocalDateTime.now());
        verify(userRepository).save(user);
    }

    @Test
    void validOtpVerifiesEmailAndClearsOtp() {
        User user = pendingUser();
        user.setEmailVerificationToken("123456");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.verifyRegistrationOtp("  LEARNER@EXAMPLE.COM ", "123456");

        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationToken()).isNull();
        assertThat(user.getVerificationTokenExpiry()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void expiredOtpIsRejected() {
        User user = pendingUser();
        user.setEmailVerificationToken("123456");
        user.setVerificationTokenExpiry(LocalDateTime.now().minusSeconds(1));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyRegistrationOtp(user.getEmail(), "123456"))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_OTP);
    }

    @Test
    void loginIsBlockedUntilRegistrationEmailIsVerified() {
        User user = pendingUser();
        user.setPasswordHash("encoded");
        user.setEmailVerificationToken("123456");
        when(userRepository.findActiveByUsernameWithRoles(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encoded")).thenReturn(true);

        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername(user.getEmail());
        request.setPassword("Password123");

        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    private User pendingUser() {
        User user = new User();
        user.setEmail("learner@example.com");
        user.setUsername("learner");
        user.setEnabled(true);
        user.setStatus("ACTIVE");
        user.setEmailVerified(false);
        return user;
    }
}
