package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.IncidentRequest;
import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.IncidentRepository;
import com.djorka.incidentops.repository.UserRepository;
import com.djorka.incidentops.dto.IncidentMetricsResponse;
import com.djorka.incidentops.exception.InvalidStatusTransitionException;
import com.djorka.incidentops.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentService {

        private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
                        "id", "title", "status", "severity", "createdAt", "updatedAt", "resolvedAt");

        private final IncidentRepository incidentRepository;
        private final UserRepository userRepository;
        private final IncidentHistoryService incidentHistoryService;

        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                                                "Authenticated user is no longer available"));
        }

        private boolean isValidStatusTransition(
                        IncidentStatus currentStatus,
                        IncidentStatus newStatus) {
                return switch (currentStatus) {
                        case OPEN ->
                                newStatus == IncidentStatus.INVESTIGATING;

                        case INVESTIGATING ->
                                newStatus == IncidentStatus.IDENTIFIED;

                        case IDENTIFIED ->
                                newStatus == IncidentStatus.MONITORING;

                        case MONITORING ->
                                newStatus == IncidentStatus.RESOLVED;

                        case RESOLVED ->
                                false;
                };
        }

        private IncidentResponse toResponse(Incident incident) {
                return new IncidentResponse(
                                incident.getId(),
                                incident.getTitle(),
                                incident.getDescription(),
                                incident.getStatus(),
                                incident.getSeverity(),
                                incident.getCreatedAt(),
                                incident.getUpdatedAt(),
                                incident.getAssignedTo() != null
                                                ? incident.getAssignedTo().getId()
                                                : null,
                                incident.getAssignedTo() != null
                                                ? incident.getAssignedTo().getName()
                                                : null,
                                incident.getResolvedAt()

                );
        }

        @Transactional
        public IncidentResponse createIncident(IncidentRequest request) {
                LocalDateTime now = LocalDateTime.now();

                Incident incident = Incident.builder()
                                .title(request.title())
                                .description(request.description())
                                .severity(request.severity())
                                .status(IncidentStatus.OPEN)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();

                Incident savedIncident = incidentRepository.save(incident);

                incidentHistoryService.recordEvent(
                                savedIncident,
                                IncidentAction.CREATED,
                                null,
                                IncidentStatus.OPEN.name(),
                                getAuthenticatedUser());

                return toResponse(savedIncident);
        }

        @Transactional(readOnly = true)
        public Page<IncidentResponse> searchIncidents(
                        IncidentStatus status,
                        IncidentSeverity severity,
                        Long assigneeId,
                        String search,
                        Pageable pageable) {

                boolean invalidSort = pageable.getSort().stream()
                                .anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));

                if (invalidSort) {
                        throw new IllegalArgumentException("Unsupported incident sort property");
                }

                Specification<Incident> specification = Specification.allOf();

                if (status != null) {
                        specification = specification.and(
                                        (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                                                        root.get("status"),
                                                        status));
                }

                if (severity != null) {
                        specification = specification.and(
                                        (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                                                        root.get("severity"),
                                                        severity));
                }

                if (assigneeId != null) {
                        specification = specification.and(
                                        (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                                                        root.get("assignedTo").get("id"),
                                                        assigneeId));
                }

                if (search != null && !search.isBlank()) {
                        String searchValue = "%" + search.toLowerCase() + "%";

                        specification = specification.and(
                                        (root, query, criteriaBuilder) -> criteriaBuilder.or(
                                                        criteriaBuilder.like(
                                                                        criteriaBuilder.lower(
                                                                                        root.get("title")),
                                                                        searchValue),
                                                        criteriaBuilder.like(
                                                                        criteriaBuilder.lower(
                                                                                        root.get("description")),
                                                                        searchValue)));
                }

                return incidentRepository
                                .findAll(specification, pageable)
                                .map(this::toResponse);
        }

        @Transactional(readOnly = true)
        public IncidentResponse getIncidentById(Long id) {
                Incident incident = incidentRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

                return toResponse(incident);
        }

        @Transactional
        public IncidentResponse updateStatus(Long id, IncidentStatus status) {
                Incident incident = incidentRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Incident not found with id: " + id));

                IncidentStatus oldStatus = incident.getStatus();

                if (!isValidStatusTransition(oldStatus, status)) {
                        throw new InvalidStatusTransitionException(
                                        "Invalid status transition from "
                                                        + oldStatus
                                                        + " to "
                                                        + status);
                }

                incident.setStatus(status);
                incident.setUpdatedAt(LocalDateTime.now());

                if (status == IncidentStatus.RESOLVED) {
                        incident.setResolvedAt(LocalDateTime.now());
                } else {
                        incident.setResolvedAt(null);
                }

                Incident updatedIncident = incidentRepository.save(incident);

                incidentHistoryService.recordEvent(
                                updatedIncident,
                                IncidentAction.STATUS_CHANGED,
                                oldStatus.name(),
                                status.name(),
                                getAuthenticatedUser());

                return toResponse(updatedIncident);
        }

        @Transactional
        public IncidentResponse updateSeverity(Long id, IncidentSeverity severity) {
                Incident incident = incidentRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Incident not found with id: " + id));

                IncidentSeverity oldSeverity = incident.getSeverity();

                incident.setSeverity(severity);
                incident.setUpdatedAt(LocalDateTime.now());

                Incident updatedIncident = incidentRepository.save(incident);

                incidentHistoryService.recordEvent(
                                updatedIncident,
                                IncidentAction.SEVERITY_CHANGED,
                                oldSeverity.name(),
                                severity.name(),
                                getAuthenticatedUser());

                return toResponse(updatedIncident);
        }

        @Transactional
        public void deleteIncident(Long id) {
                Incident incident = incidentRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Incident not found with id: " + id));

                incidentRepository.delete(incident);
        }

        @Transactional
        public IncidentResponse assignUser(Long incidentId, Long userId) {
                Incident incident = incidentRepository.findById(incidentId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Incident not found with id: " + incidentId));

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "User not found with id: " + userId));

                String oldAssignee = incident.getAssignedTo() != null
                                ? incident.getAssignedTo().getName()
                                : null;

                incident.setAssignedTo(user);
                incident.setUpdatedAt(LocalDateTime.now());

                Incident updatedIncident = incidentRepository.save(incident);

                incidentHistoryService.recordEvent(
                                updatedIncident,
                                IncidentAction.ASSIGNED,
                                oldAssignee,
                                user.getName(),
                                getAuthenticatedUser());

                return toResponse(updatedIncident);
        }

        @Transactional(readOnly = true)
        public IncidentMetricsResponse getMetrics() {
                return incidentRepository.getMetrics();
        }
}
