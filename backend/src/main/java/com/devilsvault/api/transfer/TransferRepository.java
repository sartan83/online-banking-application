package com.devilsvault.api.transfer;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("""
            SELECT t FROM Transfer t
            WHERE (t.source.owner.username = :username OR t.target.owner.username = :username)
              AND (:accountId IS NULL OR t.source.id = :accountId OR t.target.id = :accountId)
              AND (CAST(:since AS timestamp) IS NULL OR t.createdAt >= :since)
              AND (CAST(:until AS timestamp) IS NULL OR t.createdAt <= :until)
            ORDER BY t.createdAt DESC
            """)
    Page<Transfer> findVisibleTransfers(
            @Param("username") String username,
            @Param("accountId") Long accountId,
            @Param("since") OffsetDateTime since,
            @Param("until") OffsetDateTime until,
            Pageable pageable);
}
