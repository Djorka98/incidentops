package com.djorka.incidentops.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void rejectsWildcardCorsOrigin() {
        assertThatThrownBy(() -> new SecurityConfig()
                .corsConfigurationSource("http://localhost:5173, *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Wildcard CORS origins are not allowed");
    }
}
