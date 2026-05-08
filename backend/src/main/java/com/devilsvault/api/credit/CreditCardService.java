package com.devilsvault.api.credit;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for {@code dao/customer/CreditCardDaoImpl} and {@code dao/creditcard/*}.
 *
 * <p>The legacy DAO mixed account creation, card-number generation and SQL persistence; here the
 * concerns are split into {@link CreditCardNumberGenerator}, {@link CreditAccountRepository} and
 * the service. APR / credit limits are derived from the same defaults the legacy stack used
 * (APR = 2.2 %, limit = $800).
 */
@Service
public class CreditCardService {

    private static final BigDecimal DEFAULT_LIMIT = new BigDecimal("800.00");
    private static final BigDecimal DEFAULT_APR = new BigDecimal("0.0220");

    private final CreditAccountRepository accounts;
    private final CreditTransactionRepository transactions;
    private final AccountRepository depositAccounts;
    private final UserRepository users;
    private final CreditCardNumberGenerator panGenerator;

    public CreditCardService(CreditAccountRepository accounts,
                             CreditTransactionRepository transactions,
                             AccountRepository depositAccounts,
                             UserRepository users,
                             CreditCardNumberGenerator panGenerator) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.depositAccounts = depositAccounts;
        this.users = users;
        this.panGenerator = panGenerator;
    }

    public List<CreditAccount> listForOwner(String username) {
        return accounts.findByOwnerUsername(username);
    }

    public List<CreditTransaction> transactions(Long creditAccountId, String username) {
        CreditAccount card = mustOwn(creditAccountId, username);
        return transactions.findByCreditAccountIdOrderByCreatedAtDesc(card.getId());
    }

    @Transactional
    public CreditAccount openCard(String username) {
        User owner = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        CreditAccount card = new CreditAccount();
        card.setOwner(owner);
        card.setCardNumber(uniquePan());
        card.setCreditLimit(DEFAULT_LIMIT);
        card.setApr(DEFAULT_APR);
        return accounts.save(card);
    }

    @Transactional
    public CreditTransaction makePayment(Long creditAccountId, Long sourceAccountId, BigDecimal amount, String username) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        CreditAccount card = accounts.findByIdForUpdate(creditAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit card not found"));
        if (!card.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your credit card");
        }
        if (card.getStatus() != CreditAccount.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit card not active");
        }
        Account source = depositAccounts.findByIdForUpdate(sourceAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funding account not found"));
        if (!source.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Funding account not owned by user");
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds in source account");
        }

        source.setBalance(source.getBalance().subtract(amount));
        depositAccounts.save(source);
        card.setBalance(card.getBalance().subtract(amount));
        accounts.save(card);

        CreditTransaction txn = new CreditTransaction();
        txn.setCreditAccount(card);
        txn.setKind(CreditTransaction.Kind.PAYMENT);
        txn.setAmount(amount);
        txn.setSourceAccount(source);
        txn.setDescription("Payment from account " + source.getId());
        return transactions.save(txn);
    }

    private CreditAccount mustOwn(Long creditAccountId, String username) {
        CreditAccount card = accounts.findById(creditAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit card not found"));
        if (!card.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your credit card");
        }
        return card;
    }

    private String uniquePan() {
        // Bounded retry to avoid an infinite loop in the (vanishingly improbable) event of
        // collisions; in practice the first attempt always succeeds.
        for (int i = 0; i < 5; i++) {
            String candidate = panGenerator.generate();
            if (accounts.findAll().stream().noneMatch(a -> candidate.equals(a.getCardNumber()))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique card number");
    }
}
