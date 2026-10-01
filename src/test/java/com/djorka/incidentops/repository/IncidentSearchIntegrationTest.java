package com.djorka.incidentops.repository;

import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.dto.IncidentMetricsResponse;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.service.IncidentHistoryService;
import com.djorka.incidentops.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class IncidentSearchIntegrationTest {

    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private UserRepository userRepository;

    private IncidentService incidentService;
    private User ana;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository, userRepository, mock(IncidentHistoryService.class));
        ana = userRepository.save(User.builder()
                .name("Ana").email("ana@example.com").role(UserRole.RESPONDER).build());

        save("Database outage", "Primary database unavailable", IncidentStatus.OPEN,
                IncidentSeverity.SEV1, ana, LocalDateTime.of(2026, 1, 1, 10, 0));
        save("API latency", "Checkout endpoint is slow", IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV2, null, LocalDateTime.of(2026, 1, 2, 10, 0));
        save("Resolved cache issue", "Redis cache recovered", IncidentStatus.RESOLVED,
                IncidentSeverity.SEV3, ana, LocalDateTime.of(2026, 1, 3, 10, 0));
    }

    @Test
    void filtersByStatusSeverityAndAssignee() {
        Page<IncidentResponse> result = incidentService.searchIncidents(
                IncidentStatus.OPEN, IncidentSeverity.SEV1, ana.getId(), null,
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(IncidentResponse::title)
                .containsExactly("Database outage");
    }

    @Test
    void searchesTitleAndDescriptionIgnoringCase() {
        Page<IncidentResponse> titleMatch = incidentService.searchIncidents(
                null, null, null, "LATENCY", PageRequest.of(0, 10));
        Page<IncidentResponse> descriptionMatch = incidentService.searchIncidents(
                null, null, null, "redis", PageRequest.of(0, 10));

        assertThat(titleMatch.getContent()).extracting(IncidentResponse::title)
                .containsExactly("API latency");
        assertThat(descriptionMatch.getContent()).extracting(IncidentResponse::title)
                .containsExactly("Resolved cache issue");
    }

    @Test
    void paginatesAndSortsResults() {
        PageRequest firstPage = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageRequest secondPage = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<IncidentResponse> first = incidentService.searchIncidents(null, null, null, null, firstPage);
        Page<IncidentResponse> second = incidentService.searchIncidents(null, null, null, null, secondPage);

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(IncidentResponse::title)
                .containsExactly("Resolved cache issue", "API latency");
        assertThat(second.getContent()).extracting(IncidentResponse::title)
                .containsExactly("Database outage");
    }

    @Test
    void calculatesAllMetricsInOneRepositoryQuery() {
        IncidentMetricsResponse metrics = incidentRepository.getMetrics();

        assertThat(metrics).isEqualTo(
                new IncidentMetricsResponse(3, 1, 1, 0, 0, 1, 1, 1, 1, 0));
    }

    private void save(
            String title,
            String description,
            IncidentStatus status,
            IncidentSeverity severity,
            User assignee,
            LocalDateTime createdAt) {
        incidentRepository.save(Incident.builder()
                .title(title)
                .description(description)
                .status(status)
                .severity(severity)
                .assignedTo(assignee)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .resolvedAt(status == IncidentStatus.RESOLVED ? createdAt.plusHours(1) : null)
                .build());
    }
}
