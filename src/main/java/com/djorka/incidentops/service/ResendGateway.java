package com.djorka.incidentops.service;

public interface ResendGateway {

    String send(String from, String recipient, RenderedEmail email, String idempotencyKey)
            throws Exception;
}
