package com.devilsvault.api.audit;

import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modern replacement for {@code dao/log/LogDaoImpl}.
 *
 * <p>The legacy stack persisted activity into separate {@code internal_log}/{@code external_log}
 * tables; we collapse both into the existing {@code audit_log} table and rely on {@link
 * com.devilsvault.api.user.User#getRole()} (and its {@link
 * com.devilsvault.api.user.UserCategory}) at read-time to filter.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;
    private final UserRepository users;

    public AuditLogService(AuditLogRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    @Transactional
    public AuditLog record(String username, String action, String details, String ipAddress) {
        AuditLog log = new AuditLog();
        if (username != null) {
            users.findByUsername(username).map(User::getId).ifPresent(log::setUserId);
        }
        log.setAction(action);
        log.setDetails(truncate(details, 1024));
        log.setIpAddress(truncate(ipAddress, 64));
        return repository.save(log);
    }

    public List<AuditLog> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public List<AuditLog> forUser(Long userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
