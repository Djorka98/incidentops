package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentRequest(

    @NotBlank
    @Size(max = 150)
    String title,

    @NotBlank
    String description,

    @NotNull
    IncidentSeverity severity

) {
}