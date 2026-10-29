package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.EmailOutboxProcessResponse;
import com.djorka.incidentops.model.EmailOutboxStatus;
import com.djorka.incidentops.model.TransactionalEmailOutbox;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.TransactionalEmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailOutboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    @Mock
    private TransactionalEmailOutboxRepository repository;
    @Mock
    private TransactionalEmailService emailService;
    @Mock
    private TransactionalEmailTemplateRenderer renderer;
    @Mock
    private EmailDeliveryAuditService auditService;

    private EmailOutboxProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EmailOutboxProcessor(
                repository,
                emailService,
                renderer,
                auditService,
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void sendsPendingMessageAndPersistsProviderId() {
        TransactionalEmailOutbox email = email(0);
        ready(email);
        RenderedEmail rendered = new RenderedEmail("subject", "html", "text");
        when(renderer.render(any())).thenReturn(rendered);
        when(emailService.send(any(), any())).thenReturn(new EmailDeliveryResult("resend", "msg-1"));

        EmailOutboxProcessResponse response = processor.processBatch();

        assertThat(response).isEqualTo(new EmailOutboxProcessResponse(1, 1, 0, 0));
        assertThat(email.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(email.getProviderMessageId()).isEqualTo("msg-1");
        assertThat(email.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void schedulesRetryForTransientFailure() {
        TransactionalEmailOutbox email = email(0);
        ready(email);
        when(renderer.render(any())).thenReturn(new RenderedEmail("s", "h", "t"));
        when(emailService.send(any(), any()))
                .thenThrow(new EmailDeliveryException("RESEND_HTTP_500", true));

        EmailOutboxProcessResponse response = processor.processBatch();

        assertThat(response.scheduledForRetry()).isEqualTo(1);
        assertThat(email.getStatus()).isEqualTo(EmailOutboxStatus.RETRY);
        assertThat(email.getAttemptCount()).isEqualTo(1);
        assertThat(email.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void marksMessageDeadAtMaximumAttempts() {
        TransactionalEmailOutbox email = email(4);
        ready(email);
        when(renderer.render(any())).thenReturn(new RenderedEmail("s", "h", "t"));
        when(emailService.send(any(), any()))
                .thenThrow(new EmailDeliveryException("RESEND_HTTP_500", true));

        EmailOutboxProcessResponse response = processor.processBatch();

        assertThat(response.dead()).isEqualTo(1);
        assertThat(email.getStatus()).isEqualTo(EmailOutboxStatus.DEAD);
        assertThat(email.getAttemptCount()).isEqualTo(5);
    }

    private void ready(TransactionalEmailOutbox email) {
        when(repository.findReadyForProcessing(anyCollection(), any(), any()))
                .thenReturn(List.of(email));
    }

    private TransactionalEmailOutbox email(int attempts) {
        return TransactionalEmailOutbox.builder()
                .id(9L)
                .eventType(TransactionalEmailType.WELCOME)
                .user(User.builder().id(7L).email("user@example.com").name("User").build())
                .recipient("user@example.com")
                .languageCode("es")
                .payloadJson("{\"name\":\"User\",\"role\":\"VIEWER\"}")
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(attempts)
                .nextAttemptAt(NOW)
                .idempotencyKey("welcome-user/7")
                .createdAt(NOW)
                .build();
    }
}
