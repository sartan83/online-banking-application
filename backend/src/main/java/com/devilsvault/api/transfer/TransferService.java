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
        // when two users transfer in opposite directions simultaneously. Keep the locked
        // references and use them directly so the balance check cannot accidentally read
        // an unlocked copy if the persistence context is ever evicted/cleared.
        long firstId = Math.min(req.sourceAccountId(), req.targetAccountId());
        long secondId = Math.max(req.sourceAccountId(), req.targetAccountId());
        Account first = accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Account second = accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        Account source = first.getId().equals(req.sourceAccountId()) ? first : second;
        Account target = first.getId().equals(req.targetAccountId()) ? first : second;

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
