package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.EmailOutboxProcessResponse;
import com.djorka.incidentops.model.EmailDeliveryStatus;
import com.djorka.incidentops.model.EmailOutboxStatus;
import com.djorka.incidentops.model.TransactionalEmailOutbox;
import com.djorka.incidentops.repository.TransactionalEmailOutboxRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailOutboxProcessor {

    static final int BATCH_SIZE = 10;
    static final int MAX_ATTEMPTS = 5;
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailOutboxProcessor.class);

    private final TransactionalEmailOutboxRepository repository;
    private final TransactionalEmailService emailService;
    private final TransactionalEmailTemplateRenderer templateRenderer;
    private final EmailDeliveryAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock applicationClock;

    @Transactional
    public EmailOutboxProcessResponse processBatch() {
        Instant now = applicationClock.instant();
        var emails = repository.findReadyForProcessing(
                EnumSet.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY),
                now,
                PageRequest.of(0, BATCH_SIZE));
        int sent = 0;
        int retry = 0;
        int dead = 0;

        for (TransactionalEmailOutbox email : emails) {
            email.setStatus(EmailOutboxStatus.PROCESSING);
            email.setAttemptCount(email.getAttemptCount() + 1);
            repository.flush();
            TransactionalEmailMessage message = null;
            try {
                message = message(email);
                EmailDeliveryResult result = emailService.send(message, templateRenderer.render(message));
                email.setStatus(EmailOutboxStatus.SENT);
                email.setProviderMessageId(result.messageId());
                email.setSentAt(applicationClock.instant());
                email.setLastErrorCode(null);
                auditService.record(
                        message, email.getUser(), result.provider(), EmailDeliveryStatus.SENT,
                        result.messageId(), null);
                sent++;
            } catch (EmailDeliveryException exception) {
                email.setLastErrorCode(exception.getErrorCode());
                boolean shouldRetry = exception.isRetryable() && email.getAttemptCount() < MAX_ATTEMPTS;
                if (shouldRetry) {
                    email.setStatus(EmailOutboxStatus.RETRY);
                    email.setNextAttemptAt(now.plus(retryDelay(email.getAttemptCount())));
                    retry++;
                } else {
                    email.setStatus(EmailOutboxStatus.DEAD);
                    dead++;
                }
                if (message != null) {
                    auditService.record(
                            message, email.getUser(), "resend", EmailDeliveryStatus.FAILED,
                            null, exception.getErrorCode());
                }
                LOGGER.warn("Transactional email delivery failed: outboxId={}, code={}, retry={}",
                        email.getId(), exception.getErrorCode(), shouldRetry);
            }
        }
        return new EmailOutboxProcessResponse(emails.size(), sent, retry, dead);
    }

    private TransactionalEmailMessage message(TransactionalEmailOutbox email) {
        try {
            Map<String, String> payload = objectMapper.readValue(
                    email.getPayloadJson(), new TypeReference<>() { });
            return new TransactionalEmailMessage(
                    email.getEventType(),
                    email.getUser() == null ? null : email.getUser().getId(),
                    email.getRecipient(),
                    email.getLanguage(),
                    payload,
                    email.getIdempotencyKey());
        } catch (JacksonException exception) {
            throw new EmailDeliveryException("INVALID_OUTBOX_PAYLOAD", false, exception);
        }
    }

    private Duration retryDelay(int attempt) {
        return Duration.ofMinutes(1L << Math.min(attempt - 1, 4));
    }
}
