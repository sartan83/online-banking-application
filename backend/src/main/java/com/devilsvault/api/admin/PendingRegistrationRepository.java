package com.devilsvault.api.admin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    List<PendingRegistration> findByStatusOrderByCreatedAtAsc(PendingRegistration.Status status);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
