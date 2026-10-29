package com.djorka.incidentops.controller;

import com.djorka.incidentops.config.SecurityConfig;
import com.djorka.incidentops.dto.AccountPreferencesRequest;
import com.djorka.incidentops.dto.EmailOutboxProcessResponse;
import com.djorka.incidentops.model.PreferredLanguage;
import com.djorka.incidentops.service.AccountPreferencesService;
import com.djorka.incidentops.service.EmailOutboxProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AccountController.class, EmailOutboxController.class})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:5173",
        "app.email.cron-secret=test-cron-secret"
})
class TransactionalEmailSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AccountPreferencesService preferencesService;
    @MockitoBean
    private EmailOutboxProcessor processor;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void accountPreferencesRequiresAuthenticationAndUsesJwtUser() throws Exception {
        mockMvc.perform(patch("/api/account/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/account/preferences")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isNoContent());

        verify(preferencesService).update(
                7L, new AccountPreferencesRequest(PreferredLanguage.EN));
    }

    @Test
    void invalidLanguageIsRejected() throws Exception {
        mockMvc.perform(patch("/api/account/preferences")
                        .with(jwt().jwt(jwt -> jwt.claim("userId", 7L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalProcessorRequiresExactCronSecret() throws Exception {
        mockMvc.perform(get("/internal/email-outbox/process"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/email-outbox/process")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
                .andExpect(status().isUnauthorized());

        when(processor.processBatch()).thenReturn(new EmailOutboxProcessResponse(0, 0, 0, 0));
        mockMvc.perform(get("/internal/email-outbox/process")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-cron-secret"))
                .andExpect(status().isOk());
    }
}
