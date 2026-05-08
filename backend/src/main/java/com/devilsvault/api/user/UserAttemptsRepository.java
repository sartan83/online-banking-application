package com.devilsvault.api.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAttemptsRepository extends JpaRepository<UserAttempts, Long> {

    Optional<UserAttempts> findByUserUsername(String username);
}
