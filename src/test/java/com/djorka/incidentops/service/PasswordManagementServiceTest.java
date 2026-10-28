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
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.PasswordResetTokenRepository;
import com.djorka.incidentops.repository.SecurityEventRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private SecurityEventRepository eventRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private PasswordResetTokenCodec tokenCodec;
    @Mock
    private PasswordResetLinkBuilder linkBuilder;
    @Mock
    private PasswordResetDeliveryService deliveryService;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private Clock applicationClock;
    @InjectMocks
    private PasswordManagementService service;

    private User user;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(7L)
                .name("User")
                .email("User@Example.com")
                .password("current-hash")
                .role(UserRole.VIEWER)
                .build();
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("203.0.113.10");
        httpRequest.addHeader("User-Agent", "IncidentOps test");
        lenient().when(applicationClock.instant()).thenReturn(NOW);
    }

    @Test
    void changesPasswordAndRecordsSuccessfulEvent() {
        prepareValidChange();

        service.changePassword(7L, new ChangePasswordRequest("current-password", "new-password"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(tokenRepository).invalidateActiveTokens(7L, NOW);
        ArgumentCaptor<SecurityEvent> event = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(SecurityEventType.PASSWORD_CHANGED);
        assertThat(event.getValue().getEmailNormalized()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutCountingSuccess() {
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                7L, new ChangePasswordRequest("wrong-password", "new-password")))
                .isInstanceOf(PasswordOperationException.class)
                .hasMessage("Current password is invalid");

        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectsNewPasswordEqualToCurrentPassword() {
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "current-hash")).thenReturn(true);
        when(passwordEncoder.matches("same-password", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                7L, new ChangePasswordRequest("current-password", "same-password")))
                .isInstanceOf(PasswordOperationException.class)
                .hasMessage("New password must be different from current password");
    }

    @Test
    void enforcesTenMinuteSuccessfulChangeCooldown() {
        preparePasswordChecks();
        when(eventRepository.countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                eq(7L), eq(SecurityEventType.PASSWORD_CHANGED), any())).thenReturn(1L, 1L);

        assertThatThrownBy(() -> service.changePassword(
                7L, new ChangePasswordRequest("current-password", "new-password")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(eventRepository, never()).save(any());
    }

    @Test
    void permitsThreeSuccessfulChangesInTwentyFourHoursAndRejectsFourth() {
        preparePasswordChecks();
        when(passwordEncoder.encode("new-password")).thenReturn("current-hash");
        when(eventRepository.countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                eq(7L), eq(SecurityEventType.PASSWORD_CHANGED), eq(NOW.minus(PasswordManagementService.CHANGE_COOLDOWN))))
                .thenReturn(0L);
        when(eventRepository.countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                eq(7L), eq(SecurityEventType.PASSWORD_CHANGED), eq(NOW.minus(PasswordManagementService.CHANGE_WINDOW))))
                .thenReturn(0L, 1L, 2L, 3L);

        for (int index = 0; index < 3; index++) {
            service.changePassword(7L, new ChangePasswordRequest("current-password", "new-password"));
        }
        assertThatThrownBy(() -> service.changePassword(
                7L, new ChangePasswordRequest("current-password", "new-password")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(eventRepository, times(3)).save(any(SecurityEvent.class));
    }

    @Test
    void createsHashedTwentyMinuteResetTokenForExistingEmail() {
        prepareAllowedForgot(Optional.of(user));
        when(tokenCodec.generateRawToken()).thenReturn("raw-token");
        when(tokenCodec.hash("raw-token")).thenReturn("hashed-token");
        when(linkBuilder.build("raw-token")).thenReturn(Optional.of("https://frontend/reset?token=raw-token"));

        service.forgotPassword(new ForgotPasswordRequest(" User@Example.com "), httpRequest);

        ArgumentCaptor<PasswordResetToken> token = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getTokenHash()).isEqualTo("hashed-token").isNotEqualTo("raw-token");
        assertThat(token.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getValue().getExpiresAt())
                .isEqualTo(NOW.plus(PasswordManagementService.RESET_TOKEN_LIFETIME));
        verify(tokenRepository).invalidateActiveTokens(7L, NOW);
        verify(deliveryService).sendPasswordReset(
                "User@Example.com", "https://frontend/reset?token=raw-token");
    }

    @Test
    void recordsUnknownEmailRequestButDoesNotCreateToken() {
        prepareAllowedForgot(Optional.empty());

        service.forgotPassword(new ForgotPasswordRequest("missing@example.com"), httpRequest);

        verify(eventRepository).save(any(SecurityEvent.class));
        verify(tokenRepository, never()).save(any());
        verify(deliveryService, never()).sendPasswordReset(any(), any());
    }

    @Test
    void permitsThreeForgotRequestsPerEmailInOneHour() {
        prepareForgotIdentity(Optional.of(user));
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                eq("user@example.com"), eq(SecurityEventType.PASSWORD_RESET_REQUESTED),
                eq(NOW.minus(PasswordManagementService.FORGOT_HOURLY_WINDOW))))
                .thenReturn(0L, 1L, 2L);
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                eq("user@example.com"), eq(SecurityEventType.PASSWORD_RESET_REQUESTED),
                eq(NOW.minus(PasswordManagementService.FORGOT_DAILY_WINDOW))))
                .thenReturn(0L, 1L, 2L);
        when(tokenCodec.generateRawToken()).thenReturn("one", "two", "three");
        when(tokenCodec.hash(any())).thenReturn("hash-one", "hash-two", "hash-three");
        when(linkBuilder.build(any())).thenReturn(Optional.empty());

        for (int index = 0; index < 3; index++) {
            service.forgotPassword(new ForgotPasswordRequest("user@example.com"), httpRequest);
        }

        verify(tokenRepository, times(3)).save(any(PasswordResetToken.class));
    }

    @Test
    void suppressesTokenWhenHourlyEmailLimitIsReached() {
        prepareForgotIdentity(Optional.of(user));
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                eq("user@example.com"), eq(SecurityEventType.PASSWORD_RESET_REQUESTED), any()))
                .thenReturn(3L);

        service.forgotPassword(new ForgotPasswordRequest("user@example.com"), httpRequest);

        verify(tokenRepository, never()).save(any());
        verify(deliveryService, never()).sendPasswordReset(any(), any());
    }

    @Test
    void suppressesTokenWhenDailyEmailLimitIsReached() {
        prepareForgotIdentity(Optional.of(user));
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                eq("user@example.com"), eq(SecurityEventType.PASSWORD_RESET_REQUESTED),
                eq(NOW.minus(PasswordManagementService.FORGOT_HOURLY_WINDOW))))
                .thenReturn(2L);
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                eq("user@example.com"), eq(SecurityEventType.PASSWORD_RESET_REQUESTED),
                eq(NOW.minus(PasswordManagementService.FORGOT_DAILY_WINDOW))))
                .thenReturn(5L);

        service.forgotPassword(new ForgotPasswordRequest("user@example.com"), httpRequest);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void suppressesTokenWhenHourlyIpLimitIsReached() {
        prepareForgotIdentity(Optional.of(user));
        when(eventRepository.countByIpAddressAndEventTypeAndCreatedAtGreaterThanEqual(
                "203.0.113.10", SecurityEventType.PASSWORD_RESET_REQUESTED,
                NOW.minus(PasswordManagementService.FORGOT_HOURLY_WINDOW)))
                .thenReturn(10L);

        service.forgotPassword(new ForgotPasswordRequest("user@example.com"), httpRequest);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void resetsPasswordWithValidTokenAndInvalidatesOthers() {
        PasswordResetToken token = validToken();
        when(tokenCodec.hash("raw-token")).thenReturn("hashed-token");
        when(tokenRepository.findByTokenHashForUpdate("hashed-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("new-password", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(clientIpResolver.resolve(httpRequest)).thenReturn(Optional.of("203.0.113.10"));

        service.resetPassword(new ResetPasswordRequest("raw-token", "new-password"), httpRequest);

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(tokenRepository).invalidateOtherActiveTokens(7L, 11L, NOW);
        verify(eventRepository).save(any(SecurityEvent.class));
    }

    @Test
    void rejectsMissingExpiredAndUsedResetTokensWithSameMessage() {
        when(tokenCodec.hash("missing")).thenReturn("missing-hash");
        when(tokenRepository.findByTokenHashForUpdate("missing-hash")).thenReturn(Optional.empty());
        assertInvalidReset("missing");

        PasswordResetToken expired = validToken();
        expired.setExpiresAt(NOW);
        when(tokenCodec.hash("expired")).thenReturn("expired-hash");
        when(tokenRepository.findByTokenHashForUpdate("expired-hash")).thenReturn(Optional.of(expired));
        assertInvalidReset("expired");

        PasswordResetToken used = validToken();
        used.setUsedAt(NOW.minusSeconds(1));
        when(tokenCodec.hash("used")).thenReturn("used-hash");
        when(tokenRepository.findByTokenHashForUpdate("used-hash")).thenReturn(Optional.of(used));
        assertInvalidReset("used");
    }

    @Test
    void rejectsResetToCurrentPassword() {
        PasswordResetToken token = validToken();
        when(tokenCodec.hash("raw-token")).thenReturn("hashed-token");
        when(tokenRepository.findByTokenHashForUpdate("hashed-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("current-password", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest("raw-token", "current-password"), httpRequest))
                .isInstanceOf(PasswordOperationException.class)
                .hasMessage("New password must be different from current password");
    }

    private void prepareValidChange() {
        preparePasswordChecks();
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
    }

    private void preparePasswordChecks() {
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "current-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "current-hash")).thenReturn(false);
    }

    private void prepareAllowedForgot(Optional<User> foundUser) {
        prepareForgotIdentity(foundUser);
        when(eventRepository.countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
                any(), any(), any())).thenReturn(0L);
        when(eventRepository.countByIpAddressAndEventTypeAndCreatedAtGreaterThanEqual(
                any(), any(), any())).thenReturn(0L);
    }

    private void prepareForgotIdentity(Optional<User> foundUser) {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(any())).thenReturn(foundUser);
        when(clientIpResolver.resolve(httpRequest)).thenReturn(Optional.of("203.0.113.10"));
    }

    private PasswordResetToken validToken() {
        return PasswordResetToken.builder()
                .id(11L)
                .user(user)
                .tokenHash("hashed-token")
                .createdAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(60))
                .build();
    }

    private void assertInvalidReset(String rawToken) {
        assertThatThrownBy(() -> service.resetPassword(
                new ResetPasswordRequest(rawToken, "new-password"), httpRequest))
                .isInstanceOf(PasswordOperationException.class)
                .hasMessage("Password reset token is invalid or expired");
    }
}
