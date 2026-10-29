package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.AccountPreferencesRequest;
import com.djorka.incidentops.service.AccountPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountPreferencesService preferencesService;

    @PatchMapping("/preferences")
    public ResponseEntity<Void> updatePreferences(
            @Valid @RequestBody AccountPreferencesRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user identity is unavailable");
        }
        preferencesService.update(userId.longValue(), request);
        return ResponseEntity.noContent().build();
    }
}
