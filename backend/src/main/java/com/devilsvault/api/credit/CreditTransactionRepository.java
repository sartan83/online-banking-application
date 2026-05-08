package com.devilsvault.api.credit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    List<CreditTransaction> findByCreditAccountIdOrderByCreatedAtDesc(Long creditAccountId);
}
