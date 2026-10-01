package com.djorka.incidentops.config;

import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String ADMIN_PASSWORD = "temporary-bootstrap-password";

    @Mock
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private DefaultApplicationArguments applicationArguments;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        applicationArguments = new DefaultApplicationArguments(new String[0]);
    }

    @Test
    void createsAdminWhenUsersTableIsEmptyAndVariablesExist() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        AdminBootstrap bootstrap = bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

        bootstrap.run(applicationArguments);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("IncidentOps Administrator", savedUser.getName());
        assertEquals(ADMIN_EMAIL, savedUser.getEmail());
        assertEquals(UserRole.ADMIN, savedUser.getRole());
    }

    @Test
    void storesPasswordAsBcryptHashAndNeverAsPlainText() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        AdminBootstrap bootstrap = bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

        bootstrap.run(applicationArguments);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        String storedPassword = userCaptor.getValue().getPassword();
        assertFalse(storedPassword.equals(ADMIN_PASSWORD));
        assertTrue(storedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, storedPassword));
    }

    @Test
    void doesNothingWhenAUserAlreadyExists() throws Exception {
        when(userRepository.count()).thenReturn(1L);
        AdminBootstrap bootstrap = bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

        bootstrap.run(applicationArguments);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void doesNothingWhenEitherVariableIsMissingOrBlank() throws Exception {
        bootstrap("", ADMIN_PASSWORD).run(applicationArguments);
        bootstrap(ADMIN_EMAIL, " ").run(applicationArguments);

        verifyNoInteractions(userRepository);
    }

    @Test
    void runningAgainDoesNotCreateAnotherAdmin() throws Exception {
        when(userRepository.count()).thenReturn(0L, 1L);
        AdminBootstrap bootstrap = bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

        bootstrap.run(applicationArguments);
        bootstrap.run(applicationArguments);

        verify(userRepository).save(any(User.class));
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, password);
    }
}
