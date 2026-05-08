package com.devilsvault.api.admin;

import com.devilsvault.api.audit.AuditLog;
import com.devilsvault.api.audit.AuditLogService;
import com.devilsvault.api.transfer.Transfer;
import com.devilsvault.api.transfer.TransferRepository;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserAuthenticationService;
import com.devilsvault.api.user.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for the user-management portion of {@code
 * controllers/employee/EmployeeController} (user list, role transitions, account lock/unlock,
 * pending-registration approval, transfer-approval queue).
 *
 * <p>The legacy controller stitched these flows directly to JSP views via {@code ModelAndView}; we
 * keep the same business semantics but expose them as plain service methods.
 */
@Service
public class AdminService {

    private final UserRepository users;
    private final PendingRegistrationRepository pending;
    private final TransferRepository transfers;
    private final AuditLogService auditLog;
    private final PasswordEncoder encoder;
    private final UserAuthenticationService loginLimiter;

    public AdminService(UserRepository users,
                        PendingRegistrationRepository pending,
                        TransferRepository transfers,
                        AuditLogService auditLog,
                        PasswordEncoder encoder,
                        UserAuthenticationService loginLimiter) {
        this.users = users;
        this.pending = pending;
        this.transfers = transfers;
        this.auditLog = auditLog;
        this.encoder = encoder;
        this.loginLimiter = loginLimiter;
    }

    public List<User> listUsers(String adminUsername) {
        requireInternal(adminUsername);
        return users.findAll();
    }

    @Transactional
    public User changeRole(Long userId, Role newRole, String adminUsername) {
        User actor = mustFind(adminUsername);
        if (!actor.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may change roles");
        }
        User target = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        // Pattern matching switch enforces the legacy rule that staff cannot promote/demote across
        // the internal/external boundary in a single update — ADMIN must intervene explicitly.
        switch (actor.getRole()) {
            case ADMIN -> { /* full power */ }
            case MANAGER -> {
                if (target.getRole().isInternal() || newRole == Role.ADMIN) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Manager cannot reassign internal users or promote to admin");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient privileges");
        }
        target.setRole(newRole);
        return users.save(target);
    }

    @Transactional
    public User setEnabled(Long userId, boolean enabled, String adminUsername) {
        User actor = mustFind(adminUsername);
        if (!actor.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may lock/unlock accounts");
        }
        User target = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (enabled) {
            loginLimiter.unlock(target.getUsername());
        } else {
            target.setEnabled(false);
            users.save(target);
        }
        return users.findById(userId).orElseThrow();
    }

    public List<AuditLog> recentAuditLogs(String adminUsername, int limit) {
        requireInternal(adminUsername);
        return auditLog.recent(limit);
    }

    public List<Transfer> listPendingTransfers(String adminUsername) {
        requireInternal(adminUsername);
        return transfers.findAll().stream()
                .filter(t -> t.getStatus() == Transfer.Status.PENDING)
                .toList();
    }

    @Transactional
    public Transfer approveTransfer(Long transferId, String adminUsername) {
        User actor = mustFind(adminUsername);
        if (!actor.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may approve transfers");
        }
        Transfer t = transfers.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found"));
        if (t.getStatus() != Transfer.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer not pending");
        }
        t.setStatus(Transfer.Status.COMPLETED);
        return transfers.save(t);
    }

    public List<PendingRegistration> listPendingRegistrations(String adminUsername) {
        requireInternal(adminUsername);
        return pending.findByStatusOrderByCreatedAtAsc(PendingRegistration.Status.PENDING);
    }

    @Transactional
    public PendingRegistration createPendingRegistration(String requesterUsername,
                                                          CreatePendingRegistrationRequest req) {
        User requester = mustFind(requesterUsername);
        if (!requester.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may pre-register accounts");
        }
        if (requester.getRole() == Role.EMPLOYEE && req.role().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Employees may only pre-register external accounts");
        }
        if (pending.existsByUsername(req.username()) || users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (pending.existsByEmail(req.email()) || users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        PendingRegistration p = new PendingRegistration();
        p.setUsername(req.username());
        p.setEmail(req.email());
        p.setPasswordHash(encoder.encode(req.password()));
        p.setFullName(req.fullName());
        p.setRole(req.role());
        p.setRequester(requester);
        return pending.save(p);
    }

    @Transactional
    public PendingRegistration approveRegistration(Long pendingId, String approverUsername) {
        User approver = mustFind(approverUsername);
        if (!approver.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may approve registrations");
        }
        PendingRegistration p = pending.findById(pendingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending registration not found"));
        if (p.getStatus() != PendingRegistration.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration already resolved");
        }
        // Managers/admins approve anyone, but employees must not be able to promote to internal —
        // the legacy {@code preApproveUser} flow only let plain employees touch external accounts.
        if (approver.getRole() == Role.EMPLOYEE && p.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Employees may not approve internal registrations");
        }
        User u = new User();
        u.setUsername(p.getUsername());
        u.setEmail(p.getEmail());
        u.setPasswordHash(p.getPasswordHash());
        u.setFullName(p.getFullName());
        u.setRole(p.getRole());
        try {
            users.save(u);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered", ex);
        }
        p.setStatus(PendingRegistration.Status.APPROVED);
        return pending.save(p);
    }

    @Transactional
    public PendingRegistration rejectRegistration(Long pendingId, String approverUsername) {
        User approver = mustFind(approverUsername);
        if (!approver.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may reject registrations");
        }
        PendingRegistration p = pending.findById(pendingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending registration not found"));
        if (p.getStatus() != PendingRegistration.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration already resolved");
        }
        p.setStatus(PendingRegistration.Status.REJECTED);
        return pending.save(p);
    }

    private User mustFind(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private User requireInternal(String username) {
        User actor = mustFind(username);
        if (!actor.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal user role required");
        }
        return actor;
    }
}
