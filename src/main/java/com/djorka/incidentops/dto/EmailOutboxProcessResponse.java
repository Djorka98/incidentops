package com.djorka.incidentops.dto;

public record EmailOutboxProcessResponse(
        int processed,
        int sent,
        int scheduledForRetry,
        int dead
) {
}
