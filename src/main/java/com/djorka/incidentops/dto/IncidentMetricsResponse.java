package com.djorka.incidentops.dto;

public record IncidentMetricsResponse(
        long total,
        long open,
        long investigating,
        long identified,
        long monitoring,
        long resolved,
        long sev1,
        long sev2,
        long sev3,
        long sev4
) {
}