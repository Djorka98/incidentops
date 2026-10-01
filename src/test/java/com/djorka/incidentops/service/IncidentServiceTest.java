package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.IncidentMetricsResponse;
import com.djorka.incidentops.dto.IncidentRequest;
import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.exception.InvalidStatusTransitionException;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.IncidentRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IncidentHistoryService incidentHistoryService;

    @InjectMocks
    private IncidentService incidentService;

    private User authenticatedUser;

    @BeforeEach
    void setUpAuthentication() {
        authenticatedUser = user(99L, "Operator", "operator@example.com", UserRole.RESPONDER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser.getEmail(), null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsIncidentAndRecordsHistory() {
        IncidentRequest request = new IncidentRequest("API down", "Public API unavailable", IncidentSeverity.SEV1);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(1L);
            return incident;
        });
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));

        IncidentResponse response = incidentService.createIncident(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("API down");
        assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(response.severity()).isEqualTo(IncidentSeverity.SEV1);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        ArgumentCaptor<Incident> incidentCaptor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(incidentCaptor.capture());
        assertThat(incidentCaptor.getValue().getCreatedAt()).isEqualTo(incidentCaptor.getValue().getUpdatedAt());
        verify(incidentHistoryService).recordEvent(
                incidentCaptor.getValue(), IncidentAction.CREATED, null, "OPEN", authenticatedUser);
    }

    @Test
    void getsIncidentByIdAndMapsAssignee() {
        User assignee = user(7L, "Ana", "ana@example.com", UserRole.RESPONDER);
        Incident incident = incident(3L, IncidentStatus.INVESTIGATING, IncidentSeverity.SEV2);
        incident.setAssignedTo(assignee);
        when(incidentRepository.findById(3L)).thenReturn(Optional.of(incident));

        IncidentResponse response = incidentService.getIncidentById(3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.assignedUserId()).isEqualTo(7L);
        assertThat(response.assignedUserName()).isEqualTo("Ana");
    }

    @Test
    void rejectsUnknownIncidentId() {
        when(incidentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncidentById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Incident not found with id: 404");
    }

    @Test
    void rejectsUnsupportedSortPropertyBeforeQueryingDatabase() {
        assertThatThrownBy(() -> incidentService.searchIncidents(
                null, null, null, null,
                PageRequest.of(0, 10, Sort.by("password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported incident sort property");

        verify(incidentRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Incident>>any(),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void updatesStatusAndRecordsTransition() {
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV2);
        LocalDateTime previousUpdate = incident.getUpdatedAt();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));

        IncidentResponse response = incidentService.updateStatus(1L, IncidentStatus.INVESTIGATING);

        assertThat(response.status()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(response.updatedAt()).isAfterOrEqualTo(previousUpdate);
        assertThat(response.resolvedAt()).isNull();
        verify(incidentHistoryService).recordEvent(
                incident, IncidentAction.STATUS_CHANGED, "OPEN", "INVESTIGATING", authenticatedUser);
    }

    @Test
    void setsResolvedTimestampOnFinalTransition() {
        Incident incident = incident(1L, IncidentStatus.MONITORING, IncidentSeverity.SEV2);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));

        IncidentResponse response = incidentService.updateStatus(1L, IncidentStatus.RESOLVED);

        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.resolvedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidStatusTransitionWithoutSaving() {
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV2);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.updateStatus(1L, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessage("Invalid status transition from OPEN to RESOLVED");

        verify(incidentRepository, never()).save(any());
        verify(incidentHistoryService, never()).recordEvent(any(), any(), any(), any(), any());
    }

    @Test
    void updatesSeverityAndRecordsHistory() {
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV3);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));

        IncidentResponse response = incidentService.updateSeverity(1L, IncidentSeverity.SEV1);

        assertThat(response.severity()).isEqualTo(IncidentSeverity.SEV1);
        verify(incidentHistoryService).recordEvent(
                incident, IncidentAction.SEVERITY_CHANGED, "SEV3", "SEV1", authenticatedUser);
    }

    @Test
    void assignsUserAndRecordsPreviousAssignee() {
        User previous = user(2L, "Previous", "previous@example.com", UserRole.RESPONDER);
        User next = user(3L, "Next", "next@example.com", UserRole.RESPONDER);
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV2);
        incident.setAssignedTo(previous);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(3L)).thenReturn(Optional.of(next));
        when(incidentRepository.save(incident)).thenReturn(incident);
        when(userRepository.findByEmail(authenticatedUser.getEmail())).thenReturn(Optional.of(authenticatedUser));

        IncidentResponse response = incidentService.assignUser(1L, 3L);

        assertThat(response.assignedUserId()).isEqualTo(3L);
        assertThat(response.assignedUserName()).isEqualTo("Next");
        verify(incidentHistoryService).recordEvent(
                incident, IncidentAction.ASSIGNED, "Previous", "Next", authenticatedUser);
    }

    @Test
    void rejectsAssignmentWhenUserDoesNotExist() {
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV2);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.assignUser(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id: 404");
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void deletesExistingIncident() {
        Incident incident = incident(1L, IncidentStatus.OPEN, IncidentSeverity.SEV2);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        incidentService.deleteIncident(1L);

        verify(incidentRepository).delete(incident);
    }

    @Test
    void returnsMetricsFromRepositoryCounts() {
        IncidentMetricsResponse expected = new IncidentMetricsResponse(10, 3, 2, 1, 1, 3, 1, 2, 3, 4);
        when(incidentRepository.getMetrics()).thenReturn(expected);

        IncidentMetricsResponse metrics = incidentService.getMetrics();

        assertThat(metrics).isEqualTo(expected);
    }

    private Incident incident(Long id, IncidentStatus status, IncidentSeverity severity) {
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        return Incident.builder()
                .id(id)
                .title("Incident " + id)
                .description("Description")
                .status(status)
                .severity(severity)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private User user(Long id, String name, String email, UserRole role) {
        return User.builder().id(id).name(name).email(email).role(role).password("encoded").build();
    }
}
