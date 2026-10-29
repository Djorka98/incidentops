package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.AccountPreferencesRequest;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPreferencesServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private AccountPreferencesService service;

    @Test
    void updatesOnlyAuthenticatedUsersLanguage() {
        User user = User.builder().id(7L).email("user@example.com").name("User").build();
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        service.update(7L, new AccountPreferencesRequest(PreferredLanguage.EN));

        assertThat(user.getPreferredLanguage()).isEqualTo(PreferredLanguage.EN);
    }
}
