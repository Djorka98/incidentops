package com.djorka.incidentops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final ObjectProvider<AdminBootstrapService> bootstrapServiceProvider;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            ObjectProvider<AdminBootstrapService> bootstrapServiceProvider,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String adminEmail,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword) {
        this.bootstrapServiceProvider = bootstrapServiceProvider;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            return;
        }

        bootstrapServiceProvider.getObject().createAdminIfDatabaseIsEmpty(
                adminEmail.trim(),
                adminPassword);
    }
}
