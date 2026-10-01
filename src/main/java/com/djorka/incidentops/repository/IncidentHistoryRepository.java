package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.IncidentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface IncidentHistoryRepository
        extends JpaRepository<IncidentHistory, Long> {

    @EntityGraph(attributePaths = {"incident", "performedBy"})
    List<IncidentHistory> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
