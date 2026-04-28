package com.devilsvault.api.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findByActorUsernameOrderByIdAsc(String actorUsername);

    @Query("SELECT e FROM AuditEvent e WHERE "
            + "(:eventType IS NULL OR CAST(e.eventType AS string) = :eventType) AND "
            + "(:outcome IS NULL OR CAST(e.outcome AS string) = :outcome) AND "
            + "(:actorUsername IS NULL OR e.actorUsername = :actorUsername) AND "
            + "(:since IS NULL OR e.occurredAt >= :since) AND "
            + "(:until IS NULL OR e.occurredAt <= :until)")
    Page<AuditEvent> findFiltered(
            @Param("eventType") String eventType,
            @Param("outcome") String outcome,
            @Param("actorUsername") String actorUsername,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            Pageable pageable);
}
