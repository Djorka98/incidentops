package com.djorka.incidentops.controller;

import com.djorka.incidentops.config.SecurityConfig;
import com.djorka.incidentops.dto.AuthResponse;
import com.djorka.incidentops.dto.IncidentMetricsResponse;
import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.service.AuthService;
import com.djorka.incidentops.service.CommentService;
import com.djorka.incidentops.service.IncidentHistoryService;
import com.djorka.incidentops.service.IncidentService;
import com.djorka.incidentops.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        IncidentController.class,
        CommentController.class,
        IncidentHistoryController.class,
        UserController.class,
        AuthController.class
})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private CommentService commentService;
    @MockitoBean
    private IncidentHistoryService incidentHistoryService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedIncidentRequest() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadIncidents() throws Exception {
        when(incidentService.searchIncidents(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/incidents").with(role("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotCreateIncident() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(role("VIEWER"))
                        .contentType("application/json")
                        .content(validIncidentJson()))
                .andExpect(status().isForbidden());
        verify(incidentService, never()).createIncident(any());
    }

    @Test
    void responderCanCreateIncident() throws Exception {
        when(incidentService.createIncident(any())).thenReturn(incidentResponse());

        mockMvc.perform(post("/api/incidents")
                        .with(role("RESPONDER"))
                        .contentType("application/json")
                        .content(validIncidentJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void responderCannotDeleteIncident() throws Exception {
        mockMvc.perform(delete("/api/incidents/1").with(role("RESPONDER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteIncident() throws Exception {
        mockMvc.perform(delete("/api/incidents/1").with(role("ADMIN")))
                .andExpect(status().isNoContent());
        verify(incidentService).deleteIncident(1L);
    }

    @Test
    void viewerCannotReadUsersButResponderCan() throws Exception {
        mockMvc.perform(get("/api/users").with(role("VIEWER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users").with(role("RESPONDER")))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminCanCreateUsers() throws Exception {
        String body = """
                {"name":"New User","email":"new@example.com","role":"VIEWER"}
                """;

        mockMvc.perform(post("/api/users")
                        .with(role("RESPONDER"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .with(role("ADMIN"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void authenticationEndpointsArePublic() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("token"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"New User","email":"new@example.com","password":"secret123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token"));
    }

    @Test
    void mapsDuplicateRegistrationToConflict() throws Exception {
        when(authService.register(any()))
                .thenThrow(new ResourceConflictException("Email is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"Existing User","email":"used@example.com","password":"secret123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void mapsAuthenticationFailureToUnauthorizedWithoutLeakingDetails() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("internal detail"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"user@example.com","password":"incorrect"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed."));
    }

    @Test
    void rejectsNonPositiveResourceId() throws Exception {
        mockMvc.perform(get("/api/incidents/0").with(role("VIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));

        verify(incidentService, never()).getIncidentById(any());
    }

    @Test
    void validatesIncidentRequestAtControllerBoundary() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(role("RESPONDER"))
                        .contentType("application/json")
                        .content("""
                                {"title":"","description":"","severity":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.description").exists())
                .andExpect(jsonPath("$.fields.severity").exists());
        verify(incidentService, never()).createIncident(any());
    }

    @Test
    void mapsResourceNotFoundToHttp404() throws Exception {
        when(incidentService.getIncidentById(404L))
                .thenThrow(new ResourceNotFoundException("Incident not found with id: 404"));

        mockMvc.perform(get("/api/incidents/404").with(role("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found with id: 404"))
                .andExpect(jsonPath("$.path").value("/api/incidents/404"));
    }

    @Test
    void viewerCanReadCommentsAndIncidentHistory() throws Exception {
        when(commentService.getCommentsByIncident(1L)).thenReturn(List.of());
        when(incidentHistoryService.getHistoryByIncident(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/incidents/1/comments").with(role("VIEWER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/incidents/1/history").with(role("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointReturnsServiceCounts() throws Exception {
        when(incidentService.getMetrics())
                .thenReturn(new IncidentMetricsResponse(10, 3, 2, 1, 1, 3, 1, 2, 3, 4));

        mockMvc.perform(get("/api/incidents/metrics").with(role("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.resolved").value(3))
                .andExpect(jsonPath("$.sev1").value(1));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String validIncidentJson() {
        return """
                {"title":"API down","description":"Public API unavailable","severity":"SEV1"}
                """;
    }

    private IncidentResponse incidentResponse() {
        LocalDateTime now = LocalDateTime.now();
        return new IncidentResponse(
                1L, "API down", "Public API unavailable", IncidentStatus.OPEN,
                IncidentSeverity.SEV1, now, now, null, null, null);
    }
}
