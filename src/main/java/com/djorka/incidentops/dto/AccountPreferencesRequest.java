package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.PreferredLanguage;
import jakarta.validation.constraints.NotNull;

public record AccountPreferencesRequest(
        @NotNull PreferredLanguage language
) {
}
