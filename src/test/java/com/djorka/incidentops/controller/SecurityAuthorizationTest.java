package com.djorka.incidentops.controller;

import com.djorka.incidentops.config.SecurityConfig;
import com.djorka.incidentops.dto.AuthResponse;
import com.djorka.incidentops.dto.IncidentMetricsResponse;
import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.exception.PasswordOperationException;
import com.djorka.incidentops.exception.RateLimitExceededException;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.service.AuthService;
import com.djorka.incidentops.service.CommentService;
import com.djorka.incidentops.service.IncidentHistoryService;
import com.djorka.incidentops.service.IncidentService;
import com.djorka.incidentops.service.PasswordManagementService;
import com.djorka.incidentops.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173, https://frontend-production.example, ")
class SecurityAuthorizationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String SECOND_ALLOWED_ORIGIN = "https://frontend-production.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private PasswordManagementService passwordManagementService;
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
    void publicLandingIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void healthEndpointDoesNotExistAndIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/health").with(role("VIEWER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void landingResourceIsPublicAndContainsOnlyTheServiceIndicator() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("API ON", "IncidentOps API"));
    }

    @Test
    void faviconRequestIsNotRejectedBySecurity() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowsCorsPreflightFromConfiguredFrontend() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void allowsCorsPreflightFromSecondConfiguredFrontend() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", SECOND_ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", SECOND_ALLOWED_ORIGIN));
    }

    @Test
    void allowsCorsPreflightForPublicAuthenticationEndpoints() throws Exception {
        for (String endpoint : List.of("/api/auth/login", "/api/auth/register")) {
            mockMvc.perform(options(endpoint)
                            .header("Origin", ALLOWED_ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                    .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
        }
    }

    @Test
    void rejectsCorsPreflightFromUnconfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
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
    void onlyAdminCanDeleteUsers() throws Exception {
        mockMvc.perform(delete("/api/users/2").with(role("ADMIN", 1L)))
                .andExpect(status().isNoContent());
        verify(userService).deleteUser(2L, 1L);

        mockMvc.perform(delete("/api/users/2").with(role("RESPONDER", 3L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/2").with(role("VIEWER", 4L)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidUserIdBeforeDelete() throws Exception {
        mockMvc.perform(delete("/api/users/0").with(role("ADMIN", 1L)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).deleteUser(any(), any());
    }

    @Test
    void mapsUnknownUserDeleteToNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("User not found with id:404"))
                .when(userService).deleteUser(404L, 1L);

        mockMvc.perform(delete("/api/users/404").with(role("ADMIN", 1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found with id:404"));
    }

    @Test
    void mapsSelfDeleteToConflict() throws Exception {
        doThrow(new ResourceConflictException("Current user cannot be deleted"))
                .when(userService).deleteUser(1L, 1L);

        mockMvc.perform(delete("/api/users/1").with(role("ADMIN", 1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Current user cannot be deleted"));
    }

    @Test
    void mapsReferencedUserDeleteToConflict() throws Exception {
        doThrow(new ResourceConflictException(
                "User cannot be deleted because it is referenced by operational data"))
                .when(userService).deleteUser(2L, 1L);

        mockMvc.perform(delete("/api/users/2").with(role("ADMIN", 1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "User cannot be deleted because it is referenced by operational data"));
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
    void authenticatedUserCanChangePasswordButAnonymousCannot() throws Exception {
        String body = """
                {"currentPassword":"secret123","newPassword":"newSecret123"}
                """;

        mockMvc.perform(patch("/api/auth/password")
                        .with(role("VIEWER", 7L))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/auth/password")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapsPasswordChangeValidationAndRateLimit() throws Exception {
        doThrow(new PasswordOperationException("Current password is invalid"))
                .when(passwordManagementService).changePassword(eq(7L), any());
        mockMvc.perform(patch("/api/auth/password")
                        .with(role("VIEWER", 7L))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"wrong123","newPassword":"newSecret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is invalid"));

        doThrow(new RateLimitExceededException("Password change rate limit exceeded"))
                .when(passwordManagementService).changePassword(eq(8L), any());
        mockMvc.perform(patch("/api/auth/password")
                        .with(role("VIEWER", 8L))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"secret123","newPassword":"newSecret123"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forgotPasswordResponsesAreIndistinguishable() throws Exception {
        String existing = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"existing@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"missing@example.com"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(existing).isEqualTo(missing)
                .contains("If an account is associated with this email");
    }

    @Test
    void forgotPasswordRejectsInvalidEmailSyntax() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPasswordIsPublicAndMapsInvalidTokenToBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"token":"valid-token","newPassword":"newSecret123"}
                                """))
                .andExpect(status().isNoContent());

        doThrow(new PasswordOperationException("Password reset token is invalid or expired"))
                .when(passwordManagementService).resetPassword(any(), any());
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"token":"invalid-token","newPassword":"newSecret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Password reset token is invalid or expired"));
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
        return role(role, 7L);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role, Long userId) {
        return jwt()
                .jwt(builder -> builder.claim("userId", userId))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
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
