package com.djorka.incidentops.service;

import com.djorka.incidentops.exception.PasswordOperationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8 || password.length() > 100) {
            throw new PasswordOperationException("Password must be between 8 and 100 characters");
        }
    }
}
