package com.devilsvault.api.merchant;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for {@code controllers/customer/AuthorizeMerchantController} +
 * {@code MerchantPaymentController}. Captures the legacy two-step flow:
 *
 * <ol>
 *   <li>Customer creates a {@link MerchantAuthorization} for a merchant (status PENDING).</li>
 *   <li>Merchant accepts the authorisation, then issues {@link MerchantPayment payments} debiting
 *       the customer's account into the merchant's account.</li>
 * </ol>
 *
 * <p>Authorisation activation is handled by {@link #acceptAuthorization(Long, String)} so the
 * merchant explicitly opts in (the legacy stack auto-approved on creation, which we tighten
 * because it allowed any customer to "follow" a merchant without their consent).
 */
@Service
public class MerchantService {

    private final MerchantAuthorizationRepository authorizations;
    private final MerchantPaymentRepository payments;
    private final AccountRepository accounts;
    private final UserRepository users;

    public MerchantService(MerchantAuthorizationRepository authorizations,
                           MerchantPaymentRepository payments,
                           AccountRepository accounts,
                           UserRepository users) {
        this.authorizations = authorizations;
        this.payments = payments;
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
    public MerchantAuthorization authorize(String customerUsername, Long merchantId) {
        User customer = mustFind(customerUsername);
        if (customer.getRole() != Role.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customers may authorise merchants");
        }
        User merchant = users.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
        if (merchant.getRole() != Role.MERCHANT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target user is not a merchant");
        }
        return authorizations.findByCustomerIdAndMerchantId(customer.getId(), merchant.getId())
                .orElseGet(() -> {
                    MerchantAuthorization a = new MerchantAuthorization();
                    a.setCustomer(customer);
                    a.setMerchant(merchant);
                    return authorizations.save(a);
                });
    }

    @Transactional
    public MerchantAuthorization acceptAuthorization(Long authorizationId, String merchantUsername) {
        MerchantAuthorization a = authorizations.findById(authorizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authorization not found"));
        if (!a.getMerchant().getUsername().equals(merchantUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the target merchant");
        }
        if (a.getStatus() == MerchantAuthorization.Status.REVOKED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization revoked");
        }
        a.setStatus(MerchantAuthorization.Status.ACTIVE);
        return authorizations.save(a);
    }

    public List<MerchantAuthorization> listForCustomer(String customerUsername) {
        User customer = mustFind(customerUsername);
        return authorizations.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    public List<MerchantAuthorization> listForMerchant(String merchantUsername) {
        User merchant = mustFind(merchantUsername);
        return authorizations.findByMerchantIdOrderByCreatedAtDesc(merchant.getId());
    }

    @Transactional
    public MerchantPayment pay(String merchantUsername,
                               Long authorizationId,
                               Long customerAccountId,
                               Long merchantAccountId,
                               BigDecimal amount,
                               String description) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        MerchantAuthorization auth = authorizations.findById(authorizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authorization not found"));
        if (auth.getStatus() != MerchantAuthorization.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization not active");
        }
        if (!auth.getMerchant().getUsername().equals(merchantUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the merchant on this authorization");
        }

        // Lock both accounts in deterministic id order; mirrors the strategy used by TransferService.
        long firstId = Math.min(customerAccountId, merchantAccountId);
        long secondId = Math.max(customerAccountId, merchantAccountId);
        Account first = accounts.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Account second = accounts.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        Account customerAccount = first.getId().equals(customerAccountId) ? first : second;
        Account merchantAccount = first.getId().equals(merchantAccountId) ? first : second;

        if (!customerAccount.getOwner().getId().equals(auth.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account not owned by authoriser");
        }
        if (!merchantAccount.getOwner().getId().equals(auth.getMerchant().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant account not owned by merchant");
        }
        if (!customerAccount.getCurrency().equals(merchantAccount.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency mismatch");
        }
        if (customerAccount.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds");
        }

        customerAccount.setBalance(customerAccount.getBalance().subtract(amount));
        merchantAccount.setBalance(merchantAccount.getBalance().add(amount));
        accounts.save(customerAccount);
        accounts.save(merchantAccount);

        MerchantPayment p = new MerchantPayment();
        p.setAuthorization(auth);
        p.setCustomerAccount(customerAccount);
        p.setMerchantAccount(merchantAccount);
        p.setAmount(amount);
        p.setDescription(description);
        return payments.save(p);
    }

    private User mustFind(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
