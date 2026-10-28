package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.ResetPasswordRequest;
import com.djorka.incidentops.exception.PasswordOperationException;
import com.djorka.incidentops.model.PasswordResetToken;
import com.djorka.incidentops.model.SecurityEventType;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.PasswordResetTokenRepository;
import com.djorka.incidentops.repository.SecurityEventRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentops-reset-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false",
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
class PasswordResetConcurrencyIntegrationTest {

    @Autowired
    private PasswordManagementService passwordManagementService;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private SecurityEventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordResetTokenCodec tokenCodec;

    @Test
    void sameTokenCanCompleteOnlyOneConcurrentReset() throws Exception {
        String rawToken = "concurrent-reset-token";
        User user = userRepository.save(User.builder()
                .name("User")
                .email("concurrent@example.com")
                .password(passwordEncoder.encode("current-password"))
                .role(UserRole.VIEWER)
                .build());
        PasswordResetToken token = tokenRepository.saveAndFlush(PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenCodec.hash(rawToken))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1200))
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Class<?>> reset = () -> {
            ready.countDown();
            start.await();
            try {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setRemoteAddr("203.0.113.20");
                passwordManagementService.resetPassword(
                        new ResetPasswordRequest(rawToken, "new-password"), request);
                return Void.class;
            } catch (RuntimeException exception) {
                return exception.getClass();
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Class<?>>> results = List.of(executor.submit(reset), executor.submit(reset));
            ready.await();
            start.countDown();

            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(Void.class, PasswordOperationException.class);
        }

        PasswordResetToken usedToken = tokenRepository.findById(token.getId()).orElseThrow();
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(usedToken.getUsedAt()).isNotNull();
        assertThat(passwordEncoder.matches("new-password", updatedUser.getPassword())).isTrue();
        assertThat(eventRepository.countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
                user.getId(), SecurityEventType.PASSWORD_RESET_COMPLETED, Instant.EPOCH)).isEqualTo(1);
    }
}
