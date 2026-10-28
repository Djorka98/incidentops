package com.djorka.incidentops.service;

public interface PasswordResetDeliveryService {

    void sendPasswordReset(String email, String resetUrl);
}
