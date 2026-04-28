package com.devilsvault.api.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findByActorUsernameOrderByIdAsc(String actorUsername);
}
