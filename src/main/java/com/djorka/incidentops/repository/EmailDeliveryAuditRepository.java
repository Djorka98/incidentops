package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.EmailDeliveryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryAuditRepository extends JpaRepository<EmailDeliveryAudit, Long> {
}
