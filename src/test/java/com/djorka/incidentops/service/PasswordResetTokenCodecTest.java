package com.djorka.incidentops.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenCodecTest {

    private final PasswordResetTokenCodec codec = new PasswordResetTokenCodec();

    @Test
    void generatesAtLeastThirtyTwoRandomBytesAndHashesWithSha256() {
        String rawToken = codec.generateRawToken();
        String anotherToken = codec.generateRawToken();

        assertThat(Base64.getUrlDecoder().decode(rawToken)).hasSize(32);
        assertThat(rawToken).isNotEqualTo(anotherToken);
        assertThat(codec.hash(rawToken))
                .hasSize(64)
                .isEqualTo(codec.hash(rawToken))
                .isNotEqualTo(rawToken);
    }
}
