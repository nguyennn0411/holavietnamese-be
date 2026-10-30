package com.sep490.backend.modules.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sep490.backend.modules.auth.dto.request.AuthenticationRequest;
import com.sep490.backend.modules.auth.dto.request.GoogleLoginRequest;
import com.sep490.backend.modules.auth.dto.request.IntrospectRequest;
import com.sep490.backend.modules.auth.dto.request.LogoutRequest;
import com.sep490.backend.modules.auth.dto.request.RefreshRequest;
import com.sep490.backend.modules.auth.dto.request.ResetPasswordRequest;
import com.sep490.backend.modules.auth.dto.response.AuthenticationResponse;
import com.sep490.backend.modules.auth.dto.response.IntrospectResponse;
import com.sep490.backend.modules.auth.entity.Role;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.common.exception.AppException;
import com.sep490.backend.common.exception.ErrorCode;
import com.sep490.backend.common.notification.NotificationMessage;
import com.sep490.backend.common.notification.NotificationPort;
import com.sep490.backend.common.notification.NotificationType;
import com.sep490.backend.modules.auth.repository.RoleRepository;
import com.sep490.backend.modules.auth.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long REGISTRATION_OTP_EXPIRY_MINUTES = 10;

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    EmailService emailService;
    NotificationPort notifications;

    Set<String> invalidatedTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @NonFinal
    @Value("${jwt.secretKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.expiration}")
    protected long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refresh-duration}")
    protected long REFRESHABLE_DURATION;

    @NonFinal
    @Value("${google.client-id:}")
    protected String GOOGLE_CLIENT_ID;

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        String identifier = request.getUsername() != null ? request.getUsername().trim() : "";
        var user = userRepository
                .findActiveByUsernameWithRoles(identifier)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (!user.isEnabled() || !"ACTIVE".equals(user.getStatus())) throw new AppException(ErrorCode.USER_DEACTIVATED);
        if (!Boolean.TRUE.equals(user.getEmailVerified()) && user.getEmailVerificationToken() != null) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        String token = generateToken(user);
        log.info("User [{}] đã đăng nhập thành công", request.getUsername());

        Set<String> roleNames = extractRoleNames(user);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roleNames)
                .permissions(extractPermissions(user))
                .build();
    }

    @Transactional
    public AuthenticationResponse authenticateGoogle(GoogleLoginRequest request) {
        if (GOOGLE_CLIENT_ID == null || GOOGLE_CLIENT_ID.isBlank() || GOOGLE_CLIENT_ID.contains("your-google-client-id")) {
            throw new AppException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }
        GoogleIdTokenVerifier.Builder verifierBuilder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance());

        if (GOOGLE_CLIENT_ID != null && !GOOGLE_CLIENT_ID.isBlank() && !GOOGLE_CLIENT_ID.contains("your-google-client-id")) {
            verifierBuilder.setAudience(Collections.singletonList(GOOGLE_CLIENT_ID));
        }

        GoogleIdTokenVerifier verifier = verifierBuilder.build();
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.getCredential());
        } catch (Exception e) {
            log.error("Lỗi khi verify Google ID token: {}", e.getMessage());
            throw new AppException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        if (idToken == null) {
            throw new AppException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        String fullName = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        Optional<User> existingUser = userRepository.findActiveByEmailWithRoles(email);
        boolean newAccount = existingUser.isEmpty();
        User user = existingUser.orElseGet(() -> {
            Role learnerRole = roleRepository.findByName("LEARNER")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("LEARNER");
                        role.setDescription("Học viên học tiếng Việt");
                        return roleRepository.save(role);
                    });

            User newUser = new User();
            newUser.setUsername(email);
            newUser.setEmail(email);
            newUser.setFullName(fullName != null && !fullName.isBlank() ? fullName : email);
            newUser.setAvatarUrl(pictureUrl);
            newUser.setNativeLanguage("en");
            newUser.setTargetLevel(null);
            newUser.setEmailVerified(true);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setStatus("ACTIVE");
            newUser.setEnabled(true);
            newUser.setRole("LEARNER");
            newUser.setRoles(new HashSet<>(Set.of(learnerRole)));
            return userRepository.save(newUser);
        });

        if (newAccount) {
            notifications.send(user.getId(), NotificationMessage.standard(
                    "Chào mừng đến với Hola Vietnamese!",
                    "Hồ sơ học tiếng Việt của bạn đã sẵn sàng. Hãy chọn mục tiêu và bắt đầu bài học đầu tiên.",
                    NotificationType.WELCOME,
                    "/onboarding"));
        }

        if (!user.isEnabled() || !"ACTIVE".equals(user.getStatus())) {
            throw new AppException(ErrorCode.USER_DEACTIVATED);
        }

        String token = generateToken(user);
        log.info("Học viên đăng nhập Google thành công: [{}]", email);

        Set<String> roleNames = extractRoleNames(user);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roleNames)
                .permissions(extractPermissions(user))
                .build();
    }

    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.matches("\\d{6}")) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP));

        completeEmailVerification(user);
    }

    @Transactional
    public void verifyRegistrationOtp(String email, String otp) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        if (otp == null || !otp.equals(user.getEmailVerificationToken())) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        completeEmailVerification(user);
    }

    @Transactional
    public String sendRegistrationVerificationOtp(User user) {
        String otp = generateOtp();
        user.setEmailVerificationToken(otp);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(REGISTRATION_OTP_EXPIRY_MINUTES));
        userRepository.save(user);

        if (emailService.sendRegistrationVerificationOtp(
                user.getEmail(), otp, REGISTRATION_OTP_EXPIRY_MINUTES)) {
            log.info("Registration verification OTP sent to [{}]", user.getEmail());
            return null;
        }
        if (emailService.isConfigured()) {
            throw new AppException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
        log.warn("Mail is disabled; returning registration OTP in the development response for [{}]", user.getEmail());
        return otp;
    }

    private void completeEmailVerification(User user) {

        if (user.getVerificationTokenExpiry() == null
                || !user.getVerificationTokenExpiry().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
        notifications.send(user.getId(), NotificationMessage.standard(
                "Xác minh email thành công",
                "Chào mừng bạn đến với Hola Vietnamese. Hãy hoàn thiện mục tiêu học tập để bắt đầu.",
                NotificationType.WELCOME,
                "/onboarding"));
    }

    @Transactional
    public String resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return null;
        }

        String otp = generateOtp();
        user.setEmailVerificationToken(otp);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusMinutes(REGISTRATION_OTP_EXPIRY_MINUTES));
        userRepository.save(user);

        boolean sent = emailService.sendRegistrationVerificationOtp(
                user.getEmail(), otp, REGISTRATION_OTP_EXPIRY_MINUTES);
        if (!sent && emailService.isConfigured()) {
            throw new AppException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
        log.info("Registration verification OTP regenerated for [{}]", user.getEmail());
        return sent ? null : otp;
    }

    private String generateOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public String initiateForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        String otp = generateOtp();
        user.setResetPasswordOtp(otp);
        user.setResetPasswordOtpExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        boolean sent = emailService.sendPasswordResetOtp(user.getEmail(), otp);
        if (sent) {
            log.info("Đã gửi mã OTP khôi phục mật khẩu cho email [{}]", email);
            return null;
        }
        if (emailService.isConfigured()) {
            throw new AppException(ErrorCode.MAIL_DELIVERY_FAILED);
        }
        log.warn("Mail is disabled; returning password reset OTP in the development response for [{}]", email);
        return otp;
    }

    @Transactional
    public boolean verifyForgotPasswordOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getResetPasswordOtp() == null || !user.getResetPasswordOtp().equals(otp)) {
            return false;
        }
        return user.getResetPasswordOtpExpiry() != null && user.getResetPasswordOtpExpiry().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void resetPasswordWithOtp(ResetPasswordRequest request) {
        if (!verifyForgotPasswordOtp(request.getEmail(), request.getOtp())) {
            throw new RuntimeException("Mã OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordOtp(null);
        user.setResetPasswordOtpExpiry(null);
        userRepository.save(user);
        notifications.send(user.getId(), NotificationMessage.mandatory(
                "Mật khẩu đã được đặt lại",
                "Mật khẩu tài khoản của bạn vừa được đặt lại. Nếu không phải bạn thực hiện, hãy liên hệ hỗ trợ ngay.",
                NotificationType.SECURITY,
                "/settings"));
    }

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        boolean isValid = true;
        try {
            verifyToken(cleanToken(request.getToken()), false);
        } catch (Exception e) {
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).build();
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        String rawToken = cleanToken(request.getToken());
        var signedJWT = verifyToken(rawToken, true);

        var jit = signedJWT.getJWTClaimsSet().getJWTID();
        invalidatedTokens.add(jit);

        var username = signedJWT.getJWTClaimsSet().getSubject();
        var user = userRepository.findActiveByUsernameWithRoles(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var token = generateToken(user);
        log.info("Làm mới token thành công cho user [{}]", username);

        Set<String> roleNames = extractRoleNames(user);

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roleNames)
                .permissions(extractPermissions(user))
                .build();
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            log.info("Logout được gọi mà không có token hợp lệ - bỏ qua");
            return;
        }
        try {
            String rawToken = cleanToken(token);
            var signToken = verifyToken(rawToken, true);
            String jit = signToken.getJWTClaimsSet().getJWTID();
            invalidatedTokens.add(jit);
            log.info("Token [{}] đã được đưa vào blacklist thành công", jit);
        } catch (Exception e) {
            log.info("Token đã hết hạn hoặc không hợp lệ khi logout - bỏ qua");
        }
    }

    public void logout(LogoutRequest request) {
        if (request != null) {
            logout(request.getToken());
        }
    }

    public String cleanToken(String token) {
        if (token == null) return null;
        token = token.trim();
        if (token.startsWith("Bearer ")) {
            return token.substring(7).trim();
        }
        return token;
    }

    public String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername() == null ? user.getEmail() : user.getUsername())
                .issuer("holavietnamese.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(VALID_DURATION, ChronoUnit.MILLIS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .claim("userId", user.getId())
                .claim("fullName", user.getFullName())
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Không thể tạo token JWT", e);
            throw new RuntimeException(e);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = isRefresh
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime()
                        .toInstant()
                        .plus(REFRESHABLE_DURATION, ChronoUnit.MILLIS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        boolean verified = signedJWT.verify(verifier);
        if (!verified || expiryTime.before(new Date())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String jit = signedJWT.getJWTClaimsSet().getJWTID();
        if (invalidatedTokens.contains(jit)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        return signedJWT;
    }

    private String[] buildScope(User user) {
        Set<String> scopes = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                scopes.add("ROLE_" + role.getName());
                if (role.getPermissions() != null) scopes.addAll(role.getPermissions());
            });
        } else { scopes.add("ROLE_" + user.getRole()); }
        return scopes.toArray(new String[0]);
    }

    private Set<String> extractRoleNames(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) return Set.of(user.getRole());
        return user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    private Set<String> extractPermissions(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) return Set.of();
        return user.getRoles().stream()
                .filter(role -> role.getPermissions() != null)
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }
}
