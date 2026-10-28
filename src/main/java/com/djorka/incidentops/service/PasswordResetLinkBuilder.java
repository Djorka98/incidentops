package com.djorka.incidentops.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Component
public class PasswordResetLinkBuilder {

    private final String frontendUrl;

    public PasswordResetLinkBuilder(
            @Value("${app.password-reset.frontend-url:}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public Optional<String> build(String rawToken) {
        if (!StringUtils.hasText(frontendUrl)) {
            return Optional.empty();
        }

        return Optional.of(UriComponentsBuilder.fromUriString(frontendUrl.trim())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString());
    }
}
