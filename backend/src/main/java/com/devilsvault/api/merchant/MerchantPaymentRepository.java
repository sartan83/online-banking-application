package com.devilsvault.api.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPaymentRepository extends JpaRepository<MerchantPayment, Long> {
}
