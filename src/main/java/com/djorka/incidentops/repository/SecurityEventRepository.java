package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.SecurityEvent;
import com.djorka.incidentops.model.SecurityEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    long countByUserIdAndEventTypeAndCreatedAtGreaterThanEqual(
            Long userId, SecurityEventType eventType, Instant since);

    long countByEmailNormalizedAndEventTypeAndCreatedAtGreaterThanEqual(
            String emailNormalized, SecurityEventType eventType, Instant since);

    long countByIpAddressAndEventTypeAndCreatedAtGreaterThanEqual(
            String ipAddress, SecurityEventType eventType, Instant since);
}
