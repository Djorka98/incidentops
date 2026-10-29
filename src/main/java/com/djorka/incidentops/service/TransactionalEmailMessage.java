package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.TransactionalEmailType;

import java.util.Map;

public record TransactionalEmailMessage(
        TransactionalEmailType type,
        Long userId,
        String recipient,
        PreferredLanguage language,
        Map<String, String> payload,
        String idempotencyKey
) {
    public TransactionalEmailMessage {
        payload = Map.copyOf(payload);
    }
}
