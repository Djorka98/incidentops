package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.AccountPreferencesRequest;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountPreferencesService {

    private final UserRepository userRepository;

    @Transactional
    public void update(Long currentUserId, AccountPreferencesRequest request) {
        User user = userRepository.findByIdForUpdate(currentUserId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Authenticated user is no longer available"));
        user.setPreferredLanguage(request.language());
    }
}
