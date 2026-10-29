package com.djorka.incidentops.service;

import com.djorka.incidentops.model.EmailDeliveryAudit;
import com.djorka.incidentops.model.EmailDeliveryStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.EmailDeliveryAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class EmailDeliveryAuditService {

    private final EmailDeliveryAuditRepository repository;
    private final Clock applicationClock;

    public void record(
            TransactionalEmailMessage message,
            User user,
            String provider,
            EmailDeliveryStatus status,
            String providerMessageId,
            String errorCode) {
        repository.save(EmailDeliveryAudit.builder()
                .eventType(message.type())
                .user(user)
                .recipient(message.recipient())
                .languageCode(message.language().code())
                .provider(provider)
                .providerMessageId(providerMessageId)
                .status(status)
                .errorCode(errorCode)
                .createdAt(applicationClock.instant())
                .build());
    }
}
