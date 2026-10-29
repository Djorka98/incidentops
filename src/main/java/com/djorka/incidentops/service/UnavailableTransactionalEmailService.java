package com.djorka.incidentops.service;

public class UnavailableTransactionalEmailService implements TransactionalEmailService {

    @Override
    public EmailDeliveryResult send(TransactionalEmailMessage message, RenderedEmail renderedEmail) {
        throw new EmailDeliveryException("EMAIL_PROVIDER_NOT_CONFIGURED", false);
    }
}
