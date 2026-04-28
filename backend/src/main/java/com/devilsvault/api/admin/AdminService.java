package com.devilsvault.api.admin;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.account.AccountStatus;
import com.devilsvault.api.audit.AuditEventRepository;
import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.time.OffsetDateTime;
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

    public AdminService(UserRepository users, AccountRepository accounts,
                        AuditEventRepository auditRepo, AuditEventService auditService) {
        this.users = users;
        this.accounts = accounts;
        this.auditRepo = auditRepo;
        this.auditService = auditService;
    }

    public Page<AdminUserDto> listUsers(String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        if (q == null || q.isBlank()) {
            return users.findAll(pageable).map(AdminUserDto::from);
        }
        return users.searchByUsernameOrEmail(q, pageable).map(AdminUserDto::from);
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
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not active");
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
}
