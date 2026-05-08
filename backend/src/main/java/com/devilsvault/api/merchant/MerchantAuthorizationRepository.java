package com.devilsvault.api.merchant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantAuthorizationRepository extends JpaRepository<MerchantAuthorization, Long> {

    Optional<MerchantAuthorization> findByCustomerIdAndMerchantId(Long customerId, Long merchantId);

    List<MerchantAuthorization> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<MerchantAuthorization> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
}
