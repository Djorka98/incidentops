package com.djorka.incidentops.config;

import com.djorka.incidentops.service.ResendGateway;
import com.djorka.incidentops.service.ResendSdkGateway;
import com.djorka.incidentops.service.ResendTransactionalEmailService;
import com.djorka.incidentops.service.TransactionalEmailService;
import com.djorka.incidentops.service.UnavailableTransactionalEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class TransactionalEmailConfig {

    @Bean
    public TransactionalEmailService transactionalEmailService(
            @Value("${app.email.resend.api-key:}") String apiKey,
            @Value("${app.email.from-address:}") String fromEmail,
            @Value("${app.email.from-name:IncidentOps}") String fromName) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(fromEmail)) {
            return new UnavailableTransactionalEmailService();
        }
        ResendGateway gateway = new ResendSdkGateway(apiKey.trim());
        return new ResendTransactionalEmailService(gateway, fromEmail, fromName);
    }
}
