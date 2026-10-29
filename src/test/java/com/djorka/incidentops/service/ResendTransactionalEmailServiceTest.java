package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.resend.core.exception.ResendException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendTransactionalEmailServiceTest {

    @Mock
    private ResendGateway gateway;

    @Test
    void sendsCorrectSenderAndIdempotencyKey() throws Exception {
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                gateway, "no-reply@example.com", "IncidentOps");
        TransactionalEmailMessage message = message("welcome-user/42");
        RenderedEmail rendered = new RenderedEmail("Subject", "<p>Body</p>", "Body");
        when(gateway.send("IncidentOps <no-reply@example.com>", "user@example.com", rendered,
                "welcome-user/42")).thenReturn("provider-id");

        EmailDeliveryResult result = service.send(message, rendered);

        assertThat(result).isEqualTo(new EmailDeliveryResult("resend", "provider-id"));
        verify(gateway).send("IncidentOps <no-reply@example.com>", "user@example.com", rendered,
                "welcome-user/42");
    }

    @Test
    void mapsProviderErrorsWithoutExposingProviderBodyOrApiKey() throws Exception {
        ResendTransactionalEmailService service = new ResendTransactionalEmailService(
                gateway, "no-reply@example.com", "IncidentOps");
        when(gateway.send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResendException("authentication_error", 401, "contains-secret"));

        assertThatThrownBy(() -> service.send(
                message("welcome-user/42"), new RenderedEmail("s", "h", "t")))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("RESEND_HTTP_401");
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getMessage()).doesNotContain("contains-secret");
                });
    }

    private TransactionalEmailMessage message(String key) {
        return new TransactionalEmailMessage(
                TransactionalEmailType.WELCOME,
                42L,
                "user@example.com",
                PreferredLanguage.EN,
                Map.of("name", "User", "role", "VIEWER"),
                key);
    }
}
