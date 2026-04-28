package com.devilsvault.api.transfer;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final AuditEventService audit;

    public TransferService(AccountRepository accounts, TransferRepository transfers, AuditEventService audit) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.audit = audit;
    }

    @Transactional
    public Transfer execute(String username, TransferRequest req) {
        if (req.sourceAccountId().equals(req.targetAccountId())) {
            recordReject(username, req, "same_account");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target accounts must differ");
        }

        // Acquire row locks in a deterministic order (lowest id first) to avoid deadlocks
        // when two users transfer in opposite directions simultaneously. Keep the locked
        // references and use them directly so the balance check cannot accidentally read
        // an unlocked copy if the persistence context is ever evicted/cleared.
        long firstId = Math.min(req.sourceAccountId(), req.targetAccountId());
        long secondId = Math.max(req.sourceAccountId(), req.targetAccountId());
        Account first = accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> rejectAndThrow(username, req, "account_not_found", HttpStatus.NOT_FOUND, "Account not found"));
        Account second = accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> rejectAndThrow(username, req, "account_not_found", HttpStatus.NOT_FOUND, "Account not found"));

        Account source = first.getId().equals(req.sourceAccountId()) ? first : second;
        Account target = first.getId().equals(req.targetAccountId()) ? first : second;

        if (!source.getOwner().getUsername().equals(username)) {
            recordReject(username, req, "not_authorized");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to debit this account");
        }
        if (source.getStatus() == com.devilsvault.api.account.AccountStatus.FROZEN) {
            recordReject(username, req, "account_frozen");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is frozen");
        }
        if (target.getStatus() == com.devilsvault.api.account.AccountStatus.FROZEN) {
            recordReject(username, req, "account_frozen");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target account is frozen");
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            recordReject(username, req, "currency_mismatch");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency mismatch");
        }

        BigDecimal amount = req.amount();
        if (source.getBalance().compareTo(amount) < 0) {
            recordReject(username, req, "insufficient_funds");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(amount));
        accounts.save(source);
        accounts.save(target);

        Transfer t = new Transfer();
        t.setSource(source);
        t.setTarget(target);
        t.setAmount(amount);
        t.setCurrency(source.getCurrency());
        t.setDescription(req.description());
        t.setStatus(Transfer.Status.COMPLETED);
        Transfer saved = transfers.save(t);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceAccountId", req.sourceAccountId());
        payload.put("targetAccountId", req.targetAccountId());
        payload.put("amount", amount.toPlainString());
        payload.put("currency", source.getCurrency());
        // Defer until the outer transaction actually commits: the balance
        // UPDATEs and transfer INSERT here are still pending in Hibernate's
        // persistence context and will only flush at commit. If commit
        // fails we must NOT record a phantom TRANSFER_COMPLETED.
        audit.recordOnCommit(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                username, "transfer", String.valueOf(saved.getId()), payload);
        return saved;
    }

    private void recordReject(String username, TransferRequest req, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sourceAccountId", req.sourceAccountId());
        payload.put("targetAccountId", req.targetAccountId());
        payload.put("amount", req.amount() == null ? null : req.amount().toPlainString());
        payload.put("reason", reason);
        audit.record(AuditEventType.TRANSFER_REJECTED, AuditOutcome.FAILURE,
                username, "transfer", null, payload);
    }

    private ResponseStatusException rejectAndThrow(
            String username, TransferRequest req, String reason, HttpStatus status, String message) {
        recordReject(username, req, reason);
        return new ResponseStatusException(status, message);
    }
}
