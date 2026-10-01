package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.djorka.incidentops.dto.IncidentMetricsResponse;

public interface IncidentRepository
                extends JpaRepository<Incident, Long>,
                JpaSpecificationExecutor<Incident> {

        @Override
        @EntityGraph(attributePaths = "assignedTo")
        Page<Incident> findAll(Specification<Incident> specification, Pageable pageable);

        @Query("""
                        select new com.djorka.incidentops.dto.IncidentMetricsResponse(
                                count(i),
                                coalesce(sum(case when i.status = com.djorka.incidentops.model.IncidentStatus.OPEN then 1 else 0 end), 0),
                                coalesce(sum(case when i.status = com.djorka.incidentops.model.IncidentStatus.INVESTIGATING then 1 else 0 end), 0),
                                coalesce(sum(case when i.status = com.djorka.incidentops.model.IncidentStatus.IDENTIFIED then 1 else 0 end), 0),
                                coalesce(sum(case when i.status = com.djorka.incidentops.model.IncidentStatus.MONITORING then 1 else 0 end), 0),
                                coalesce(sum(case when i.status = com.djorka.incidentops.model.IncidentStatus.RESOLVED then 1 else 0 end), 0),
                                coalesce(sum(case when i.severity = com.djorka.incidentops.model.IncidentSeverity.SEV1 then 1 else 0 end), 0),
                                coalesce(sum(case when i.severity = com.djorka.incidentops.model.IncidentSeverity.SEV2 then 1 else 0 end), 0),
                                coalesce(sum(case when i.severity = com.djorka.incidentops.model.IncidentSeverity.SEV3 then 1 else 0 end), 0),
                                coalesce(sum(case when i.severity = com.djorka.incidentops.model.IncidentSeverity.SEV4 then 1 else 0 end), 0)
                        )
                        from Incident i
                        """)
        IncidentMetricsResponse getMetrics();
}
