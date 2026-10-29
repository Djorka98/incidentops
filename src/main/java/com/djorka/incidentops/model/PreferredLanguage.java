package com.djorka.incidentops.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PreferredLanguage {
    ES("es"),
    EN("en");

    private final String code;

    PreferredLanguage(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static PreferredLanguage fromCode(String value) {
        if (value == null) {
            return null;
        }
        for (PreferredLanguage language : values()) {
            if (language.code.equalsIgnoreCase(value.trim())) {
                return language;
            }
        }
        throw new IllegalArgumentException("Unsupported language");
    }
}
