package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.AuthResponse;
import com.djorka.incidentops.dto.ChangePasswordRequest;
import com.djorka.incidentops.dto.ForgotPasswordRequest;
import com.djorka.incidentops.dto.LoginRequest;
import com.djorka.incidentops.dto.MessageResponse;
import com.djorka.incidentops.dto.RegisterRequest;
import com.djorka.incidentops.dto.ResetPasswordRequest;
import com.djorka.incidentops.service.AuthService;
import com.djorka.incidentops.service.PasswordManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordManagementService passwordManagementService;

    private static final String FORGOT_PASSWORD_MESSAGE =
            "If an account is associated with this email, password reset instructions will be sent.";

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        passwordManagementService.changePassword(currentUserId(jwt), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordManagementService.forgotPassword(request, httpRequest);
        return ResponseEntity.accepted().body(new MessageResponse(FORGOT_PASSWORD_MESSAGE));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        passwordManagementService.resetPassword(request, httpRequest);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user identity is unavailable");
        }
        return userId.longValue();
    }
}
