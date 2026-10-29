package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.PreferredLanguage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank
        @Email
        @Size(max = 150)
        String email,
        PreferredLanguage language
) {
    public ForgotPasswordRequest(String email) {
        this(email, null);
    }
}
