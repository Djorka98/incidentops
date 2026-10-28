package com.djorka.incidentops.config;

import com.djorka.incidentops.service.PasswordResetDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PasswordResetDeliveryConfig {

    @Bean
    @ConditionalOnMissingBean(PasswordResetDeliveryService.class)
    public PasswordResetDeliveryService noOpPasswordResetDeliveryService() {
        return (email, resetUrl) -> {
            // Delivery provider intentionally not configured. Never log resetUrl.
        };
    }
}
