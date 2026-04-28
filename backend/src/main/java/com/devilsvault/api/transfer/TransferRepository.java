package com.devilsvault.api.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public interface TransferRepository extends JpaRepository<Transfer, Long> {

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

    @Query("SELECT t FROM Transfer t WHERE (t.source.id = :acctId OR t.target.id = :acctId)"
            + " AND t.status = :status"
            + " AND (t.createdAt > :fromAt OR (t.createdAt = :fromAt AND t.id >= :fromId))"
            + " AND (t.createdAt < :toAt OR (t.createdAt = :toAt AND t.id <= :toId))"
            + " ORDER BY t.createdAt ASC, t.id ASC")
    List<Transfer> findAllBetweenInclusive(@Param("acctId") Long acctId,
                                           @Param("status") Transfer.Status status,
                                           @Param("fromAt") OffsetDateTime fromAt,
                                           @Param("fromId") Long fromId,
                                           @Param("toAt") OffsetDateTime toAt,
                                           @Param("toId") Long toId);
}
