package com.djorka.incidentops.config;

import com.djorka.incidentops.service.PasswordResetDeliveryService;
import com.djorka.incidentops.service.EmailDeliveryAuditService;
import com.djorka.incidentops.service.TransactionalEmailService;
import com.djorka.incidentops.service.TransactionalEmailTemplateRenderer;
import com.djorka.incidentops.service.TransactionalPasswordResetDeliveryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordResetDeliveryConfig {

    @Bean
    public PasswordResetDeliveryService passwordResetDeliveryService(
            TransactionalEmailService emailService,
            TransactionalEmailTemplateRenderer templateRenderer,
            EmailDeliveryAuditService auditService) {
        return new TransactionalPasswordResetDeliveryService(
                emailService, templateRenderer, auditService);
    }
}
