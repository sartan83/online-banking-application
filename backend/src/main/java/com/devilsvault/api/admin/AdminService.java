package com.devilsvault.api.admin;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountStatus;
import com.devilsvault.api.audit.AuditEventRepository;
import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.auth.RefreshTokenService;
import com.devilsvault.api.crypto.EncryptionService;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final AuditEventRepository auditRepo;
    private final AuditEventService auditService;
    private final RefreshTokenService refreshTokenService;
    private final EncryptionService encryption;

    public AdminService(UserRepository users, AccountRepository accounts,
                        AuditEventRepository auditRepo, AuditEventService auditService,
                        RefreshTokenService refreshTokenService, EncryptionService encryption) {
        this.users = users;
        this.accounts = accounts;
        this.auditRepo = auditRepo;
        this.auditService = auditService;
        this.refreshTokenService = refreshTokenService;
        this.encryption = encryption;
    }

    public Page<AdminUserDto> listUsers(String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        if (q == null || q.isBlank()) {
            return users.findAll(pageable).map(AdminUserDto::from);
        }
        if (q.contains("@")) {
            String hash = encryption.sha256Hex(q.toLowerCase(Locale.ROOT));
            Page<User> byHash = users.findByEmailHash(hash, pageable);
            if (!byHash.isEmpty()) {
                return byHash.map(AdminUserDto::from);
            }
        }
        return users.searchByUsername(q, pageable).map(AdminUserDto::from);
    }

    public AdminUserDetailDto getUser(Long id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var accs = accounts.findByOwnerId(user.getId()).stream()
                .map(AdminAccountDto::from)
                .toList();
        return AdminUserDetailDto.from(user, accs);
    }

    @Transactional
    public AdminAccountDto freezeAccount(Long accountId, String adminUsername) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is already frozen");
        }
        account.setStatus(AccountStatus.FROZEN);
        accounts.save(account);
        auditService.recordOnCommit(AuditEventType.ACCOUNT_FROZEN, AuditOutcome.SUCCESS,
                adminUsername, "account", String.valueOf(accountId), Map.of());
        return AdminAccountDto.from(account);
    }

    @Transactional
    public AdminAccountDto unfreezeAccount(Long accountId, String adminUsername) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != AccountStatus.FROZEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not frozen");
        }
        account.setStatus(AccountStatus.ACTIVE);
        accounts.save(account);
        auditService.recordOnCommit(AuditEventType.ACCOUNT_UNFROZEN, AuditOutcome.SUCCESS,
                adminUsername, "account", String.valueOf(accountId), Map.of());
        return AdminAccountDto.from(account);
    }

    public Page<AdminAuditDto> listAudit(String eventType, String outcome, String actorUsername,
                                          OffsetDateTime since, OffsetDateTime until,
                                          int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return auditRepo.findFiltered(eventType, outcome, actorUsername, since, until, pageable)
                .map(AdminAuditDto::from);
    }

    public AdminIntegrityDto checkAuditIntegrity(String adminUsername) {
        AuditEventService.ChainStatus status = auditService.verifyChain();
        long total = auditRepo.count();
        auditService.record(AuditEventType.AUDIT_INTEGRITY_CHECK,
                status.valid() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE,
                adminUsername, "audit", null, Map.of("valid", status.valid()));
        return new AdminIntegrityDto(status.valid(), status.firstInvalidId(), total);
    }

    @Transactional
    public void revokeUserSessions(Long userId, String adminUsername) {
        User target = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        refreshTokenService.revokeAllForUser(target);
        auditService.record(AuditEventType.SESSION_REVOKED, AuditOutcome.SUCCESS,
                adminUsername, "user", String.valueOf(userId),
                Map.of("targetUsername", target.getUsername()));
    }
}
