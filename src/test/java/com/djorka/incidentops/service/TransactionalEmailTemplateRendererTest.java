package com.djorka.incidentops.service;

import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.model.TransactionalEmailType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionalEmailTemplateRendererTest {

    private final TransactionalEmailTemplateRenderer renderer = new TransactionalEmailTemplateRenderer();

    @Test
    void rendersAllSpanishAndEnglishTemplatesWithPlainTextFallback() {
        for (PreferredLanguage language : PreferredLanguage.values()) {
            for (TransactionalEmailType type : TransactionalEmailType.values()) {
                RenderedEmail rendered = renderer.render(message(type, language));
                assertThat(rendered.subject()).isNotBlank();
                assertThat(rendered.html()).contains("IncidentOps").doesNotContain("<script");
                assertThat(rendered.text()).contains("IncidentOps").isNotBlank();
                assertThat(rendered.html()).doesNotContain("secret-password");
                assertThat(rendered.text()).doesNotContain("secret-password");
            }
        }
    }

    @Test
    void resetTemplatesContainLocalizedCopyAndTwentyMinuteExpiry() {
        RenderedEmail spanish = renderer.render(message(
                TransactionalEmailType.PASSWORD_RESET, PreferredLanguage.ES));
        RenderedEmail english = renderer.render(message(
                TransactionalEmailType.PASSWORD_RESET, PreferredLanguage.EN));

        assertThat(spanish.subject()).isEqualTo("Restablece tu contraseña de IncidentOps");
        assertThat(spanish.text()).contains("20 minutos", "Restablecer contraseña");
        assertThat(english.subject()).isEqualTo("Reset your IncidentOps password");
        assertThat(english.text()).contains("20 minutes", "Reset password");
    }

    @Test
    void escapesUntrustedTemplateValues() {
        TransactionalEmailMessage message = new TransactionalEmailMessage(
                TransactionalEmailType.WELCOME,
                1L,
                "user@example.com",
                PreferredLanguage.ES,
                Map.of("name", "<script>alert(1)</script>", "role", "VIEWER"),
                "welcome-user/1");

        assertThat(renderer.render(message).html())
                .doesNotContain("<script>alert")
                .contains("&lt;script&gt;");
    }

    private TransactionalEmailMessage message(
            TransactionalEmailType type, PreferredLanguage language) {
        return new TransactionalEmailMessage(
                type,
                1L,
                "user@example.com",
                language,
                Map.of(
                        "name", "Alex",
                        "role", "VIEWER",
                        "occurredAt", "2026-10-28T12:00:00Z",
                        "ip", "203.0.113.10",
                        "userAgent", "Chrome",
                        "resetUrl", "https://frontend/reset?token=raw-token",
                        "password", "secret-password"),
                type.name().toLowerCase() + "/1");
    }
}
