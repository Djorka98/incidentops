package com.djorka.incidentops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.djorka.incidentops.model.PreferredLanguage;

public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        PreferredLanguage language

) {
    public RegisterRequest(String name, String email, String password) {
        this(name, email, password, null);
    }
}
