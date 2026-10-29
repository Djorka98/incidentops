package com.djorka.incidentops.service;

import com.resend.Resend;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;

public class ResendSdkGateway implements ResendGateway {

    private final Resend resend;

    public ResendSdkGateway(String apiKey) {
        this.resend = new Resend(apiKey);
    }

    @Override
    public String send(String from, String recipient, RenderedEmail email, String idempotencyKey)
            throws Exception {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(recipient)
                .subject(email.subject())
                .html(email.html())
                .text(email.text())
                .build();
        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        return resend.emails().send(options, requestOptions).getId();
    }
}
