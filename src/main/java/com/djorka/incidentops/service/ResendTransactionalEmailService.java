package com.djorka.incidentops.service;

import com.resend.core.exception.ResendException;

public class ResendTransactionalEmailService implements TransactionalEmailService {

    private final ResendGateway gateway;
    private final String from;

    public ResendTransactionalEmailService(ResendGateway gateway, String fromEmail, String fromName) {
        this.gateway = gateway;
        this.from = sanitize(fromName) + " <" + sanitize(fromEmail) + ">";
    }

    @Override
    public EmailDeliveryResult send(TransactionalEmailMessage message, RenderedEmail renderedEmail) {
        try {
            String messageId = gateway.send(
                    from, message.recipient(), renderedEmail, message.idempotencyKey());
            return new EmailDeliveryResult("resend", messageId);
        } catch (ResendException exception) {
            Integer status = exception.getStatusCode();
            boolean retryable = status == null || status == 408 || status == 429 || status >= 500;
            String code = status == null ? "RESEND_ERROR" : "RESEND_HTTP_" + status;
            throw new EmailDeliveryException(code, retryable, exception);
        } catch (Exception exception) {
            throw new EmailDeliveryException("RESEND_NETWORK_ERROR", true, exception);
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "").trim();
    }
}
