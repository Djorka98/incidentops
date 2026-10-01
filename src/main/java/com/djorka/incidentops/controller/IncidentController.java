package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.IncidentRequest;
import com.djorka.incidentops.dto.IncidentResponse;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.service.IncidentService;
import com.djorka.incidentops.dto.IncidentMetricsResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Validated
public class IncidentController {

        private final IncidentService incidentService;

        @PostMapping
        public ResponseEntity<IncidentResponse> createIncident(
                        @Valid @RequestBody IncidentRequest request) {
                IncidentResponse response = incidentService.createIncident(request);

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @GetMapping
        public ResponseEntity<Page<IncidentResponse>> getIncidents(
                        @RequestParam(required = false) IncidentStatus status,
                        @RequestParam(required = false) IncidentSeverity severity,
                        @RequestParam(required = false) @Positive Long assigneeId,
                        @RequestParam(required = false) @Size(max = 200) String search,
                        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

                return ResponseEntity.ok(
                                incidentService.searchIncidents(
                                                status,
                                                severity,
                                                assigneeId,
                                                search,
                                                pageable));
        }

        @GetMapping("/{id}")
        public ResponseEntity<IncidentResponse> getIncidentById(
                        @PathVariable @Positive Long id) {
                return ResponseEntity.ok(
                                incidentService.getIncidentById(id));
        }

        @PatchMapping("/{id}/status")
        public ResponseEntity<IncidentResponse> updateStatus(
                        @PathVariable @Positive Long id,
                        @RequestParam IncidentStatus status) {
                return ResponseEntity.ok(
                                incidentService.updateStatus(id, status));
        }

        @PatchMapping("/{id}/severity")
        public ResponseEntity<IncidentResponse> updateSeverity(
                        @PathVariable @Positive Long id,
                        @RequestParam IncidentSeverity severity) {
                return ResponseEntity.ok(
                                incidentService.updateSeverity(id, severity));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteIncident(
                        @PathVariable @Positive Long id) {
                incidentService.deleteIncident(id);

                return ResponseEntity.noContent().build();
        }

        @PatchMapping("/{incidentId}/assignee/{userId}")
        public ResponseEntity<IncidentResponse> assignUser(
                        @PathVariable @Positive Long incidentId,
                        @PathVariable @Positive Long userId) {
                return ResponseEntity.ok(
                                incidentService.assignUser(incidentId, userId));
        }

        @GetMapping("/metrics")
        public ResponseEntity<IncidentMetricsResponse> getMetrics() {
                return ResponseEntity.ok(
                                incidentService.getMetrics());
        }
}
