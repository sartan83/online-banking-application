package com.devilsvault.api.otp;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpRepository extends JpaRepository<Otp, Long> {

    Optional<Otp> findFirstByUserIdAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(Long userId, Otp.Purpose purpose);

    long deleteByUserIdAndPurpose(Long userId, Otp.Purpose purpose);
}
