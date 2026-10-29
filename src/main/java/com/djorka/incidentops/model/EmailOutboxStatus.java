package com.djorka.incidentops.model;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    DEAD
}
