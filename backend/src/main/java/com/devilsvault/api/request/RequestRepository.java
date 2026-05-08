package com.devilsvault.api.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByStatusOrderByCreatedAtAsc(Request.Status status);

    List<Request> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<Request> findByScopeAndStatusOrderByCreatedAtAsc(Request.Scope scope, Request.Status status);
}
