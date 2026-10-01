package com.djorka.incidentops.config;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    void acceptsBase64Encoded256BitSecret() {
        byte[] keyBytes = new byte[32];

        SecretKey key = jwtConfig.jwtSecretKey(Base64.getEncoder().encodeToString(keyBytes));

        assertThat(key.getEncoded()).hasSize(32);
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void rejectsSecretThatIsNotBase64() {
        assertThatThrownBy(() -> jwtConfig.jwtSecretKey("not-valid-base64"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must be valid Base64.");
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> jwtConfig.jwtSecretKey(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must contain at least 256 bits.");
    }
}
