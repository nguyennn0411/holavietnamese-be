package com.sep490.backend.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sep490.backend.dto.request.AuthenticationRequest;
import com.sep490.backend.dto.request.IntrospectRequest;
import com.sep490.backend.dto.request.LogoutRequest;
import com.sep490.backend.dto.response.AuthenticationResponse;
import com.sep490.backend.dto.response.IntrospectResponse;
import com.sep490.backend.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationService {

    final PasswordEncoder passwordEncoder;

    @Value("${jwt.signerKey:this_is_a_very_secret_key_that_should_be_at_least_64_bytes_long_for_hs512}")
    protected String signerKey;

    // For simple mock implementation, a set of logged out token IDs
    private final Set<String> invalidatedTokens = new HashSet<>();

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // Mock authentication - replace with actual DB lookup
        boolean authenticated = false;
        if ("admin".equals(request.getUsername()) && "admin".equals(request.getPassword())) {
            authenticated = true;
        }

        if (!authenticated) {
            throw new RuntimeException(ErrorCode.UNAUTHENTICATED.getMessage());
        }

        String token = generateToken(request.getUsername());

        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        String token = request.getToken();
        boolean isValid = true;
        
        try {
            verifyToken(token);
        } catch (Exception e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .valid(isValid)
                .build();
    }
    
    public IntrospectResponse introspect(String token) throws JOSEException, ParseException {
        return introspect(new IntrospectRequest(token));
    }

    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            SignedJWT signToken = verifyToken(request.getToken());
            String jit = signToken.getJWTClaimsSet().getJWTID();
            
            // In a real app, save to DB with expiry time
            invalidatedTokens.add(jit);
        } catch (Exception e) {
            // Token is already invalid or expired, just ignore
        }
    }

    private SignedJWT verifyToken(String token) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        boolean verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date())))
            throw new RuntimeException("Unauthenticated");

        if (invalidatedTokens.contains(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new RuntimeException("Unauthenticated");

        return signedJWT;
    }

    private String generateToken(String username) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(username)
                .issuer("com.sep490.backend")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()
                ))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "ROLE_ADMIN") // Mock role
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
