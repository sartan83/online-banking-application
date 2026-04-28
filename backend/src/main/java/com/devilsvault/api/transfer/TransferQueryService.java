package com.devilsvault.api.transfer;

import com.devilsvault.api.account.Account;
import com.devilsvault.api.account.AccountRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferQueryService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;

    public TransferQueryService(TransferRepository transfers, AccountRepository accounts) {
        this.transfers = transfers;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public TransferListResponse list(String username, Long accountId,
                                     OffsetDateTime since, OffsetDateTime until,
                                     int page, int size) {
        if (accountId != null) {
            Set<Long> ownedIds = accounts.findByOwnerUsername(username).stream()
                    .map(Account::getId)
                    .collect(Collectors.toSet());
            if (!ownedIds.contains(accountId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Not authorized to view transfers for this account");
            }
        }

        int clampedSize = Math.min(Math.max(size, 1), 100);
        int clampedPage = Math.max(page, 0);
        Page<Transfer> result = transfers.findVisibleTransfers(
                username, accountId, since, until, PageRequest.of(clampedPage, clampedSize));

        Set<Long> ownedAccountIds = accounts.findByOwnerUsername(username).stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        List<TransferItemDto> items = result.getContent().stream()
                .map(t -> TransferItemDto.from(t, computeDirection(t, ownedAccountIds)))
                .toList();

        return new TransferListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * DEBIT when the caller's account is the source; CREDIT when it's the target.
     * When both sides belong to the caller, DEBIT is returned (own-account transfer).
     */
    private TransferDirection computeDirection(Transfer t, Set<Long> ownedAccountIds) {
        if (ownedAccountIds.contains(t.getSource().getId())) {
            return TransferDirection.DEBIT;
        }
        return TransferDirection.CREDIT;
    }
}
