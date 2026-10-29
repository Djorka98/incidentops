package com.djorka.incidentops.service;

import com.djorka.incidentops.model.EmailOutboxStatus;
import com.djorka.incidentops.model.TransactionalEmailOutbox;
import com.djorka.incidentops.model.TransactionalEmailType;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.TransactionalEmailOutboxRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentops-email-outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false",
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
class EmailOutboxConcurrencyIntegrationTest {

    @Autowired
    private EmailOutboxProcessor processor;
    @Autowired
    private TransactionalEmailOutboxRepository outboxRepository;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private TransactionalEmailService emailService;

    @Test
    void concurrentProcessorsDoNotDeliverSameOutboxMessageTwice() throws Exception {
        outboxRepository.deleteAll();
        User user = userRepository.save(User.builder()
                .name("Outbox User")
                .email("outbox@example.com")
                .role(UserRole.VIEWER)
                .build());
        TransactionalEmailOutbox email = outboxRepository.save(TransactionalEmailOutbox.builder()
                .eventType(TransactionalEmailType.WELCOME)
                .user(user)
                .recipient(user.getEmail())
                .languageCode("es")
                .payloadJson("{\"name\":\"Outbox User\",\"role\":\"VIEWER\"}")
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.parse("2026-10-28T00:00:00Z"))
                .idempotencyKey("welcome-user/" + user.getId())
                .createdAt(Instant.parse("2026-10-28T00:00:00Z"))
                .build());

        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch enteredDelivery = new CountDownLatch(1);
        when(emailService.send(any(), any())).thenAnswer(invocation -> {
            deliveries.incrementAndGet();
            enteredDelivery.countDown();
            Thread.sleep(150);
            return new EmailDeliveryResult("resend", "message-id");
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(processor::processBatch);
            assertThat(enteredDelivery.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(processor::processBatch);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertThat(deliveries).hasValue(1);
        assertThat(outboxRepository.findById(email.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.SENT);
    }
}
