package com.djorka.incidentops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.djorka.incidentops.model.PreferredLanguage;

public record LoginRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 100)
        String password,

        PreferredLanguage language

) {
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
