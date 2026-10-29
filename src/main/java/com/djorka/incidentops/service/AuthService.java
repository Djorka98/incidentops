package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.AuthResponse;
import com.djorka.incidentops.dto.LoginRequest;
import com.djorka.incidentops.dto.RegisterRequest;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.SecurityEvent;
import com.djorka.incidentops.model.SecurityEventType;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.djorka.incidentops.repository.SecurityEventRepository;
import com.djorka.incidentops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PreferredLanguageResolver languageResolver;
    private final EmailOutboxService emailOutboxService;
    private final SecurityEventRepository securityEventRepository;
    private final ClientIpResolver clientIpResolver;
    private final Clock applicationClock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceConflictException(
                    "Email is already registered"
            );
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.VIEWER)
                .build();
        user.setPreferredLanguage(languageResolver.resolve(request.language(), null));

        User savedUser = userRepository.save(user);
        emailOutboxService.enqueue(
                TransactionalEmailType.WELCOME,
                savedUser,
                savedUser.getPreferredLanguage(),
                Map.of("name", savedUser.getName(), "role", savedUser.getRole().name()),
                "welcome-user/" + savedUser.getId());

        return new AuthResponse(
                jwtService.generateToken(savedUser)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        return login(
                request,
                clientIpResolver.resolve(httpRequest).orElse(null),
                httpRequest.getHeader("User-Agent"));
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        PreferredLanguage language = languageResolver.resolve(request.language(), user);
        if (request.language() != null) {
            user.setPreferredLanguage(language);
        }
        Instant now = applicationClock.instant();
        SecurityEvent signInEvent = securityEventRepository.saveAndFlush(SecurityEvent.builder()
                .user(user)
                .emailNormalized(user.getEmail().trim().toLowerCase(Locale.ROOT))
                .eventType(SecurityEventType.NEW_SIGN_IN)
                .ipAddress(ipAddress)
                .userAgent(safeUserAgent(userAgent))
                .createdAt(now)
                .build());
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("occurredAt", now.toString());
        if (ipAddress != null) {
            payload.put("ip", ipAddress);
        }
        String safeUserAgent = safeUserAgent(userAgent);
        if (safeUserAgent != null) {
            payload.put("userAgent", safeUserAgent);
        }
        emailOutboxService.enqueue(
                TransactionalEmailType.NEW_SIGN_IN,
                user,
                language,
                payload,
                "new-sign-in/" + signInEvent.getId());

        return new AuthResponse(
                jwtService.generateToken(user)
        );
    }

    private String safeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String compact = userAgent.trim().replaceAll("\\s+", " ");
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }
}
