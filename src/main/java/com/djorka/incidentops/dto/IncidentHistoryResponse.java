package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.IncidentAction;

import java.time.LocalDateTime;

public record IncidentHistoryResponse(
        Long id,
        Long incidentId,
        IncidentAction action,
        String oldValue,
        String newValue,
        Long performedByUserId,
        String performedByUserName,
        LocalDateTime createdAt
) {
}