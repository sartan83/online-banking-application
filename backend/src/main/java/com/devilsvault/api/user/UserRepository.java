package com.devilsvault.api.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmailSearchHash(String emailSearchHash);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> searchByUsername(@Param("q") String q, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.emailSearchHash = :hash")
    Page<User> findByEmailHash(@Param("hash") String hash, Pageable pageable);
}
