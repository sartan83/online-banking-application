package com.devilsvault.api.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
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

    @Query("SELECT t FROM Transfer t WHERE (t.source.id = :acctId OR t.target.id = :acctId)"
            + " AND t.status = :status AND t.createdAt >= :since AND t.createdAt <= :until")
    Page<Transfer> findAllByAccount(@Param("acctId") Long acctId,
                                    @Param("since") OffsetDateTime since,
                                    @Param("until") OffsetDateTime until,
                                    @Param("status") Transfer.Status status,
                                    Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.source.id = :acctId"
            + " AND t.status = :status AND t.createdAt >= :since AND t.createdAt <= :until")
    Page<Transfer> findDebitsByAccount(@Param("acctId") Long acctId,
                                       @Param("since") OffsetDateTime since,
                                       @Param("until") OffsetDateTime until,
                                       @Param("status") Transfer.Status status,
                                       Pageable pageable);

    @Query("SELECT t FROM Transfer t WHERE t.target.id = :acctId"
            + " AND t.status = :status AND t.createdAt >= :since AND t.createdAt <= :until")
    Page<Transfer> findCreditsByAccount(@Param("acctId") Long acctId,
                                        @Param("since") OffsetDateTime since,
                                        @Param("until") OffsetDateTime until,
                                        @Param("status") Transfer.Status status,
                                        Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t"
            + " WHERE t.source.id = :acctId AND t.status = :status")
    BigDecimal sumAllDebits(@Param("acctId") Long acctId, @Param("status") Transfer.Status status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t"
            + " WHERE t.target.id = :acctId AND t.status = :status")
    BigDecimal sumAllCredits(@Param("acctId") Long acctId, @Param("status") Transfer.Status status);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t"
            + " WHERE t.source.id = :acctId AND t.status = :status"
            + " AND (t.createdAt < :before OR (t.createdAt = :before AND t.id < :beforeId))")
    BigDecimal sumDebitsBefore(@Param("acctId") Long acctId,
                               @Param("status") Transfer.Status status,
                               @Param("before") OffsetDateTime before,
                               @Param("beforeId") Long beforeId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transfer t"
            + " WHERE t.target.id = :acctId AND t.status = :status"
            + " AND (t.createdAt < :before OR (t.createdAt = :before AND t.id < :beforeId))")
    BigDecimal sumCreditsBefore(@Param("acctId") Long acctId,
                                @Param("status") Transfer.Status status,
                                @Param("before") OffsetDateTime before,
                                @Param("beforeId") Long beforeId);
}
