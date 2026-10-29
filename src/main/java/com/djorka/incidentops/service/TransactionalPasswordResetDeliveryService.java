package com.djorka.incidentops.service;

import com.djorka.incidentops.model.EmailDeliveryStatus;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.djorka.incidentops.model.User;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class TransactionalPasswordResetDeliveryService implements PasswordResetDeliveryService {

    private final TransactionalEmailService emailService;
    private final TransactionalEmailTemplateRenderer templateRenderer;
    private final EmailDeliveryAuditService auditService;

    @Override
    public void sendPasswordReset(String email, String resetUrl) {
        send(null, email, resetUrl, PreferredLanguage.ES, "password-reset/legacy/" + UUID.randomUUID());
    }

    @Override
    public void sendPasswordReset(
            User user,
            String resetUrl,
            PreferredLanguage language,
            Long resetTokenId) {
        send(user, user.getEmail(), resetUrl, language, "password-reset/token/" + resetTokenId);
    }

    private void send(
            User user,
            String recipient,
            String resetUrl,
            PreferredLanguage language,
            String idempotencyKey) {
        TransactionalEmailMessage message = new TransactionalEmailMessage(
                TransactionalEmailType.PASSWORD_RESET,
                user == null ? null : user.getId(),
                recipient,
                language,
                Map.of("resetUrl", resetUrl),
                idempotencyKey);
        auditService.record(message, user, "resend", EmailDeliveryStatus.REQUESTED, null, null);
        try {
            EmailDeliveryResult result = emailService.send(message, templateRenderer.render(message));
            auditService.record(
                    message, user, result.provider(), EmailDeliveryStatus.SENT,
                    result.messageId(), null);
        } catch (EmailDeliveryException exception) {
            auditService.record(
                    message, user, "resend", EmailDeliveryStatus.FAILED,
                    null, exception.getErrorCode());
            throw exception;
        }
    }
}
