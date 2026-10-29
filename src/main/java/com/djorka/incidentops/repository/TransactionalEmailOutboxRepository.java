package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.EmailOutboxStatus;
import com.djorka.incidentops.model.TransactionalEmailOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TransactionalEmailOutboxRepository extends JpaRepository<TransactionalEmailOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select email from TransactionalEmailOutbox email
            where email.status in :statuses and email.nextAttemptAt <= :now
            order by email.id
            """)
    List<TransactionalEmailOutbox> findReadyForProcessing(
            @Param("statuses") Collection<EmailOutboxStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);
}
