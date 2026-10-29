package com.djorka.incidentops.service;

public interface TransactionalEmailService {

    EmailDeliveryResult send(TransactionalEmailMessage message, RenderedEmail renderedEmail);
}
