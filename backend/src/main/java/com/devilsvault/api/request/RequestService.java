package com.devilsvault.api.request;

import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserCategory;
import com.devilsvault.api.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for the legacy {@code RequestDaoImpl} family
 * ({@code InternalRequestDaoImpl}/{@code ExternalRequestDaoImpl}).
 *
 * <p>The legacy stack stored two pending tables and applied the requested mutation directly via
 * dynamic SQL on approval ({@code update("internal_user", set, newValue, ...)}); we instead apply
 * mutations through the same domain repositories (so JPA invariants and audit advice still fire)
 * via {@link RequestApplier} hooks. Phase 2 ports the request lifecycle and persistence; the
 * applier hooks default to a no-op and are wired into concrete behaviours in subsequent phases.
 */
@Service
public class RequestService {

    private final RequestRepository repository;
    private final UserRepository users;

    public RequestService(RequestRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    public List<Request> list(Request.Status status) {
        if (status == null) {
            return repository.findAll();
        }
        return repository.findByStatusOrderByCreatedAtAsc(status);
    }

    public List<Request> listForUser(String username) {
        User u = mustFind(username);
        return repository.findByRequesterIdOrderByCreatedAtDesc(u.getId());
    }

    @Transactional
    public Request create(String username, CreateRequest req) {
        User requester = mustFind(username);
        Request r = new Request();
        r.setRequester(requester);
        r.setRequestType(req.requestType());
        r.setCurrentValue(req.currentValue());
        r.setRequestedValue(req.requestedValue());
        r.setDescription(req.description());
        r.setScope(scopeFor(requester));
        return repository.save(r);
    }

    @Transactional
    public Request approve(Long requestId, String approverUsername) {
        return resolve(requestId, approverUsername, Request.Status.APPROVED);
    }

    @Transactional
    public Request reject(Long requestId, String approverUsername) {
        return resolve(requestId, approverUsername, Request.Status.REJECTED);
    }

    private Request resolve(Long requestId, String approverUsername, Request.Status target) {
        Request r = repository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (r.getStatus() != Request.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request already resolved");
        }
        User approver = mustFind(approverUsername);
        if (!approver.getRole().isInternal()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only internal users may resolve requests");
        }
        r.setApprover(approver);
        // Pattern matching switch on the sealed UserCategory hierarchy makes the legacy
        // INTERNAL/EXTERNAL branching explicit and exhaustive at compile time.
        Request.Scope scope = switch (r.getRequester().getRole().category()) {
            case UserCategory.Internal __ -> Request.Scope.INTERNAL;
            case UserCategory.External __ -> Request.Scope.EXTERNAL;
        };
        r.setScope(scope);
        r.setStatus(target);
        return repository.save(r);
    }

    private static Request.Scope scopeFor(User user) {
        return switch (user.getRole().category()) {
            case UserCategory.Internal __ -> Request.Scope.INTERNAL;
            case UserCategory.External __ -> Request.Scope.EXTERNAL;
        };
    }

    private User mustFind(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
