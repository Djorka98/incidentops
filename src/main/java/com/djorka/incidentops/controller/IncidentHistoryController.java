package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.IncidentHistoryResponse;
import com.djorka.incidentops.service.IncidentHistoryService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents/{incidentId}/history")
@RequiredArgsConstructor
@Validated
public class IncidentHistoryController {

    private final IncidentHistoryService incidentHistoryService;

    @GetMapping
    public ResponseEntity<List<IncidentHistoryResponse>> getHistoryByIncident(
            @PathVariable @Positive Long incidentId
    ) {
        return ResponseEntity.ok(
                incidentHistoryService.getHistoryByIncident(incidentId)
        );
    }
}
