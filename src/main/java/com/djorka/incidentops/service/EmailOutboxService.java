package com.djorka.incidentops.service;

import com.djorka.incidentops.model.EmailDeliveryStatus;
import com.djorka.incidentops.model.EmailOutboxStatus;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.TransactionalEmailOutbox;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.TransactionalEmailOutboxRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailOutboxService {

    private final TransactionalEmailOutboxRepository repository;
    private final EmailDeliveryAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    public TransactionalEmailOutbox enqueue(
            TransactionalEmailType type,
            User user,
            PreferredLanguage language,
            Map<String, String> payload,
            String idempotencyKey) {
        Instant now = applicationClock.instant();
        TransactionalEmailMessage message = new TransactionalEmailMessage(
                type, user.getId(), user.getEmail(), language, payload, idempotencyKey);
        TransactionalEmailOutbox outbox = TransactionalEmailOutbox.builder()
                .eventType(type)
                .user(user)
                .recipient(user.getEmail())
                .payloadJson(toJson(payload))
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .build();
        outbox.setLanguage(language);
        outbox = repository.save(outbox);
        auditService.record(message, user, "resend", EmailDeliveryStatus.REQUESTED, null, null);
        return outbox;
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize transactional email metadata", exception);
        }
    }
}
