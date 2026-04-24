package com.devilsvault.api.transfer;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;

    public TransferService(AccountRepository accounts, TransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional
    public Transfer execute(String username, TransferRequest req) {
        if (req.sourceAccountId().equals(req.targetAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and target accounts must differ");
        }

        // Acquire row locks in a deterministic order (lowest id first) to avoid deadlocks
        // when two users transfer in opposite directions simultaneously.
        long firstId = Math.min(req.sourceAccountId(), req.targetAccountId());
        long secondId = Math.max(req.sourceAccountId(), req.targetAccountId());
        accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        Account source = accounts.findById(req.sourceAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found"));
        Account target = accounts.findById(req.targetAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target account not found"));

        if (!source.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to debit this account");
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency mismatch");
        }

        BigDecimal amount = req.amount();
        if (source.getBalance().compareTo(amount) < 0) {
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
        return transfers.save(t);
    }
}
