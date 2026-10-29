package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.User;

public interface PasswordResetDeliveryService {

    void sendPasswordReset(String email, String resetUrl);

    default void sendPasswordReset(
            User user,
            String resetUrl,
            PreferredLanguage language,
            Long resetTokenId) {
        sendPasswordReset(user.getEmail(), resetUrl);
    }
}
