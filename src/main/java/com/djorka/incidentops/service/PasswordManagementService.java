package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.ChangePasswordRequest;
import com.djorka.incidentops.dto.ForgotPasswordRequest;
import com.djorka.incidentops.dto.ResetPasswordRequest;
import com.djorka.incidentops.exception.PasswordOperationException;
import com.djorka.incidentops.exception.RateLimitExceededException;
import com.djorka.incidentops.model.PasswordResetToken;
import com.djorka.incidentops.model.SecurityEvent;
import com.djorka.incidentops.model.SecurityEventType;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.PasswordResetTokenRepository;
import com.djorka.incidentops.repository.SecurityEventRepository;
import com.djorka.incidentops.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordManagementService {

    static final Duration CHANGE_COOLDOWN = Duration.ofMinutes(10);
    static final Duration CHANGE_WINDOW = Duration.ofHours(24);
    static final int MAX_CHANGES_PER_WINDOW = 3;
    static final Duration FORGOT_HOURLY_WINDOW = Duration.ofHours(1);
    static final int MAX_FORGOT_PER_HOUR = 3;
    static final Duration FORGOT_DAILY_WINDOW = Duration.ofHours(24);
    static final int MAX_FORGOT_PER_DAY = 5;
    static final int MAX_FORGOT_PER_IP_HOUR = 10;
    static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(20);
    private static final String INVALID_RESET_TOKEN_MESSAGE =
            "Password reset token is invalid or expired";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecurityEventRepository securityEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordResetTokenCodec tokenCodec;
    private final PasswordResetLinkBuilder resetLinkBuilder;
    private final PasswordResetDeliveryService deliveryService;
    private final ClientIpResolver clientIpResolver;
    private final Clock applicationClock;

    @Transactional
    public void changePassword(Long currentUserId, ChangePasswordRequest request) {
        User user = userRepository.findByIdForUpdate(currentUserId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Authenticated user is no longer available"));
        passwordPolicy.validate(request.newPassword());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new PasswordOperationException("Current password is invalid");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new PasswordOperationException("New password must be different from current password");
        }

        Instant now = applicationClock.instant();
        long recentChanges = securityEventRepository
                .countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                        currentUserId, SecurityEventType.PASSWORD_CHANGED, now.minus(CHANGE_COOLDOWN));
        long dailyChanges = securityEventRepository
                .countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                        currentUserId, SecurityEventType.PASSWORD_CHANGED, now.minus(CHANGE_WINDOW));
        if (recentChanges > 0 || dailyChanges >= MAX_CHANGES_PER_WINDOW) {
            throw new RateLimitExceededException("Password change rate limit exceeded");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);
        securityEventRepository.save(securityEvent(
                user, normalizeEmail(user.getEmail()), SecurityEventType.PASSWORD_CHANGED,
                null, null, now));
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String normalizedEmail = normalizeEmail(request.email());
        Instant now = applicationClock.instant();
        String clientIp = clientIpResolver.resolve(httpRequest).orElse(null);
        String userAgent = safeUserAgent(httpRequest.getHeader("User-Agent"));

        Optional<User> user = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail);
        if (forgotLimitExceeded(normalizedEmail, clientIp, now)) {
            return;
        }

        securityEventRepository.save(securityEvent(
                user.orElse(null), normalizedEmail, SecurityEventType.PASSWORD_RESET_REQUESTED,
                clientIp, userAgent, now));
        if (user.isEmpty()) {
            return;
        }

        passwordResetTokenRepository.invalidateActiveTokens(user.get().getId(), now);
        String rawToken = tokenCodec.generateRawToken();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .user(user.get())
                .tokenHash(tokenCodec.hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(RESET_TOKEN_LIFETIME))
                .build());

        resetLinkBuilder.build(rawToken).ifPresent(resetUrl -> {
            try {
                deliveryService.sendPasswordReset(user.get().getEmail(), resetUrl);
            } catch (RuntimeException ignored) {
                // The public response stays generic. A provider can add private monitoring.
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        passwordPolicy.validate(request.newPassword());
        Instant now = applicationClock.instant();
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashForUpdate(tokenCodec.hash(request.token()))
                .orElseThrow(this::invalidResetToken);

        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now) || token.getUser() == null) {
            throw invalidResetToken();
        }

        User user = token.getUser();
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new PasswordOperationException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(now);
        passwordResetTokenRepository.invalidateOtherActiveTokens(user.getId(), token.getId(), now);
        securityEventRepository.save(securityEvent(
                user, normalizeEmail(user.getEmail()), SecurityEventType.PASSWORD_RESET_COMPLETED,
                clientIpResolver.resolve(httpRequest).orElse(null),
                safeUserAgent(httpRequest.getHeader("User-Agent")), now));
    }

    private boolean forgotLimitExceeded(String email, String ipAddress, Instant now) {
        long emailHourly = securityEventRepository
                .countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                        email, SecurityEventType.PASSWORD_RESET_REQUESTED,
                        now.minus(FORGOT_HOURLY_WINDOW));
        long emailDaily = securityEventRepository
                .countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                        email, SecurityEventType.PASSWORD_RESET_REQUESTED,
                        now.minus(FORGOT_DAILY_WINDOW));
        if (emailHourly >= MAX_FORGOT_PER_HOUR || emailDaily >= MAX_FORGOT_PER_DAY) {
            return true;
        }

        return ipAddress != null && securityEventRepository
                .countByIpAddressAndEventTypeAndCreatedAtGreaterThanEqual(
                        ipAddress, SecurityEventType.PASSWORD_RESET_REQUESTED,
                        now.minus(FORGOT_HOURLY_WINDOW)) >= MAX_FORGOT_PER_IP_HOUR;
    }

    private SecurityEvent securityEvent(
            User user,
            String email,
            SecurityEventType type,
            String ipAddress,
            String userAgent,
            Instant createdAt) {
        return SecurityEvent.builder()
                .user(user)
                .emailNormalized(email)
                .eventType(type)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(createdAt)
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String safeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }

    private PasswordOperationException invalidResetToken() {
        return new PasswordOperationException(INVALID_RESET_TOKEN_MESSAGE);
    }
}
