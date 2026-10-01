package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.IncidentHistoryResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
import com.djorka.incidentops.model.IncidentHistory;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.IncidentHistoryRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentHistoryService {

    private final IncidentHistoryRepository incidentHistoryRepository;
    private final IncidentRepository incidentRepository;

    private IncidentHistoryResponse toResponse(IncidentHistory history) {

        User performedBy = history.getPerformedBy();

        return new IncidentHistoryResponse(
                history.getId(),
                history.getIncident().getId(),
                history.getAction(),
                history.getOldValue(),
                history.getNewValue(),
                performedBy != null ? performedBy.getId() : null,
                performedBy != null ? performedBy.getName() : null,
                history.getCreatedAt()
        );
    }

    public void recordEvent(
            Incident incident,
            IncidentAction action,
            String oldValue,
            String newValue,
            User performedBy
    ) {
        IncidentHistory history = IncidentHistory.builder()
                .incident(incident)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .createdAt(LocalDateTime.now())
                .build();

        incidentHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<IncidentHistoryResponse> getHistoryByIncident(Long incidentId) {

        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException(
                    "Incident not found with id: " + incidentId
            );
        }

        return incidentHistoryRepository
                .findByIncidentIdOrderByCreatedAtAsc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}
