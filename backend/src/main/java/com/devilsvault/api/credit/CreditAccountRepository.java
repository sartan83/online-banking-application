package com.devilsvault.api.credit;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    List<CreditAccount> findByOwnerUsername(String username);

    /** Locks both the credit account and its owner so concurrent payments can't overdraw. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CreditAccount c WHERE c.id = :id")
    Optional<CreditAccount> findByIdForUpdate(@Param("id") Long id);
}
