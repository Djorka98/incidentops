package com.djorka.incidentops.config;

import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {

    private static final String ADMIN_NAME = "IncidentOps Administrator";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createAdminIfDatabaseIsEmpty(String adminEmail, String adminPassword) {
        if (userRepository.count() != 0) {
            return;
        }

        User admin = User.builder()
                .name(ADMIN_NAME)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
    }
}
