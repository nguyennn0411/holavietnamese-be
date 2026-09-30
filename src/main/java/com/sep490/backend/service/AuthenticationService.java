package com.sep490.backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sep490.backend.dto.request.AuthenticationRequest;
import com.sep490.backend.dto.request.GoogleLoginRequest;
import com.sep490.backend.dto.request.IntrospectRequest;
import com.sep490.backend.dto.request.LogoutRequest;
import com.sep490.backend.dto.request.RefreshRequest;
import com.sep490.backend.dto.response.AuthenticationResponse;
import com.sep490.backend.dto.response.IntrospectResponse;
import com.sep490.backend.entity.Role;
import com.sep490.backend.entity.User;
import com.sep490.backend.exception.AppException;
import com.sep490.backend.exception.ErrorCode;
import com.sep490.backend.repository.RoleRepository;
import com.sep490.backend.repository.UserRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;

    /**
     * Blacklist lưu tạm trong bộ nhớ (in-memory).
     * Khi cần production-grade đa server, có thể chuyển sang Redis.
     */
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

    // ──────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────

    /**
     * Xác thực người dùng bằng username/password và trả về JWT + thông tin cơ bản.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var user = userRepository
                .findActiveByUsernameWithRoles(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (!"ACTIVE".equals(user.getStatus())) throw new AppException(ErrorCode.USER_DEACTIVATED);

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
                .build();
    }

    /**
     * Xác thực người dùng qua Google ID Token (Sign in with Google).
     */
    @Transactional
    public AuthenticationResponse authenticateGoogle(GoogleLoginRequest request) {
        GoogleIdTokenVerifier.Builder verifierBuilder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance());

        // Nếu client ID đã được cấu hình thật (không phải placeholder), xác thực đúng audience
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

        // Tìm user theo email hoặc tạo mới nếu chưa tồn tại
        User user = userRepository.findActiveByEmailWithRoles(email).orElseGet(() -> {
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
            newUser.setTargetLevel("A1");
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setStatus("ACTIVE");
            newUser.setRoles(new HashSet<>(Set.of(learnerRole)));
            return userRepository.save(newUser);
        });

        if (!"ACTIVE".equals(user.getStatus())) {
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
                .build();
    }

    /**
     * Kiểm tra xem token có hợp lệ không (dùng bởi CustomJwtDecoder và client).
     */
    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        boolean isValid = true;
        try {
            verifyToken(cleanToken(request.getToken()), false);
        } catch (Exception e) {
            isValid = false;
        }
        return IntrospectResponse.builder().valid(isValid).build();
    }

    /**
     * Làm mới token (Refresh token).
     */
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
                .build();
    }

    /**
     * Đăng xuất: đưa JWT ID vào blacklist.
     */
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

    // ──────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────

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
                .subject(user.getUsername())
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
            user.getRoles().forEach(role -> scopes.add("ROLE_" + role.getName()));
        }
        return scopes.toArray(new String[0]);
    }

    private Set<String> extractRoleNames(User user) {
        if (user.getRoles() == null) return Set.of();
        return user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}
