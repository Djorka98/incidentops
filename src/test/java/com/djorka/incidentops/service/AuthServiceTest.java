package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.AuthResponse;
import com.djorka.incidentops.dto.LoginRequest;
import com.djorka.incidentops.dto.RegisterRequest;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.UserRepository;
import com.djorka.incidentops.repository.SecurityEventRepository;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.SecurityEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Spy
    private PreferredLanguageResolver languageResolver = new PreferredLanguageResolver();
    @Mock
    private EmailOutboxService emailOutboxService;
    @Mock
    private SecurityEventRepository securityEventRepository;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private Clock applicationClock;
    @InjectMocks
    private AuthService authService;

    @Test
    void registersViewerWithEncodedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("New User", "new@example.com", "secret123");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(20L);
            return user;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.VIEWER);
        assertThat(captor.getValue().getPreferredLanguage()).isEqualTo(PreferredLanguage.ES);
        verify(emailOutboxService).enqueue(
                eq(com.djorka.incidentops.model.TransactionalEmailType.WELCOME),
                eq(captor.getValue()), eq(PreferredLanguage.ES), any(), eq("welcome-user/20"));
    }

    @Test
    void registerPersistsExplicitEnglishLanguage() {
        RegisterRequest request = new RegisterRequest(
                "New User", "new@example.com", "secret123", PreferredLanguage.EN);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(21L);
            return user;
        });

        authService.register(request);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
    }

    @Test
    void rejectsDuplicateRegistration() {
        RegisterRequest request = new RegisterRequest("User", "used@example.com", "secret123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("Email is already registered");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticatesLoginAndReturnsToken() {
        LoginRequest request = new LoginRequest("user@example.com", "secret123");
        User user = User.builder().id(1L).name("User").email(request.email()).role(UserRole.VIEWER).build();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(applicationClock.instant()).thenReturn(Instant.parse("2026-10-28T12:00:00Z"));
        when(securityEventRepository.saveAndFlush(any(SecurityEvent.class)))
                .thenReturn(SecurityEvent.builder().id(9L).build());
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        verify(emailOutboxService).enqueue(
                eq(com.djorka.incidentops.model.TransactionalEmailType.NEW_SIGN_IN),
                eq(user), eq(PreferredLanguage.ES), any(), eq("new-sign-in/9"));
    }

    @Test
    void loginOptionalLanguageUpdatesPreferenceWithoutChangingJwtContract() {
        LoginRequest request = new LoginRequest(
                "user@example.com", "secret123", PreferredLanguage.EN);
        User user = User.builder().id(1L).name("User").email(request.email()).role(UserRole.VIEWER).build();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(applicationClock.instant()).thenReturn(Instant.parse("2026-10-28T12:00:00Z"));
        when(securityEventRepository.saveAndFlush(any(SecurityEvent.class)))
                .thenReturn(SecurityEvent.builder().id(10L).build());
        when(jwtService.generateToken(user)).thenReturn("unchanged-jwt");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("unchanged-jwt");
        assertThat(user.getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
    }

    @Test
    void propagatesMissingUserAfterSuccessfulAuthentication() {
        LoginRequest request = new LoginRequest("missing@example.com", "secret123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(jwtService, never()).generateToken(any());
    }
}
