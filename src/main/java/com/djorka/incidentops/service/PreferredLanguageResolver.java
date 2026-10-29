package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.User;
import org.springframework.stereotype.Component;

@Component
public class PreferredLanguageResolver {

    public PreferredLanguage resolve(PreferredLanguage explicit, User user) {
        if (explicit != null) {
            return explicit;
        }
        if (user != null && user.getPreferredLanguage() != null) {
            return user.getPreferredLanguage();
        }
        return PreferredLanguage.ES;
    }
}
