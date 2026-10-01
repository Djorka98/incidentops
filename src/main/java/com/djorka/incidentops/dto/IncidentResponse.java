package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;

import java.time.LocalDateTime;

public record IncidentResponse(
    Long id,
    String title,
    String description,
    IncidentStatus status,
    IncidentSeverity severity,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long assignedUserId,
    String assignedUserName,
    LocalDateTime resolvedAt
) {
}