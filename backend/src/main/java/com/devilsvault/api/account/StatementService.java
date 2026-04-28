package com.devilsvault.api.account;

import com.devilsvault.api.transfer.Transfer;
import com.devilsvault.api.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatementService {

    private static final String DIRECTION_DEBIT = "DEBIT";
    private static final String DIRECTION_CREDIT = "CREDIT";

    private final AccountRepository accounts;
    private final TransferRepository transfers;

    public StatementService(AccountRepository accounts, TransferRepository transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @Transactional(readOnly = true)
    public AccountDetailDto getAccountDetail(String username, Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this account");
        }
        return AccountDetailDto.from(account);
    }

    @Transactional(readOnly = true)
    public StatementPage getStatement(String username, Long accountId,
                                      OffsetDateTime since, OffsetDateTime until,
                                      String direction, Pageable pageable) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getOwner().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this account");
        }

        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.parse("1970-01-01T00:00:00Z");
        OffsetDateTime effectiveUntil = until != null ? until : OffsetDateTime.parse("9999-12-31T23:59:59Z");

        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        PageRequest sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Page<Transfer> page = fetchFilteredPage(accountId, effectiveSince, effectiveUntil, direction, sortedPageable);

        if (page.isEmpty()) {
            return new StatementPage(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }

        BigDecimal balanceBeforePage = computeBalanceBeforeEntry(account, accountId, page.getContent().get(0));
        List<StatementEntry> entries = buildEntries(page.getContent(), accountId, balanceBeforePage);

        return new StatementPage(
                entries,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private Page<Transfer> fetchFilteredPage(Long accountId, OffsetDateTime since, OffsetDateTime until,
                                              String direction, Pageable pageable) {
        Transfer.Status completed = Transfer.Status.COMPLETED;
        if (DIRECTION_DEBIT.equals(direction)) {
            return transfers.findDebitsByAccount(accountId, since, until, completed, pageable);
        } else if (DIRECTION_CREDIT.equals(direction)) {
            return transfers.findCreditsByAccount(accountId, since, until, completed, pageable);
        }
        return transfers.findAllByAccount(accountId, since, until, completed, pageable);
    }

    private BigDecimal computeBalanceBeforeEntry(Account account, Long accountId, Transfer firstEntry) {
        Transfer.Status completed = Transfer.Status.COMPLETED;
        BigDecimal totalDebits = transfers.sumAllDebits(accountId, completed);
        BigDecimal totalCredits = transfers.sumAllCredits(accountId, completed);
        BigDecimal openingBalance = account.getBalance().add(totalDebits).subtract(totalCredits);

        BigDecimal debitsBefore = transfers.sumDebitsBefore(accountId, completed,
                firstEntry.getCreatedAt(), firstEntry.getId());
        BigDecimal creditsBefore = transfers.sumCreditsBefore(accountId, completed,
                firstEntry.getCreatedAt(), firstEntry.getId());

        return openingBalance.subtract(debitsBefore).add(creditsBefore);
    }

    private List<StatementEntry> buildEntries(List<Transfer> pageTransfers, Long accountId,
                                               BigDecimal balanceBeforePage) {
        List<StatementEntry> entries = new ArrayList<>();
        BigDecimal runningBalance = balanceBeforePage;

        for (Transfer t : pageTransfers) {
            boolean isDebit = t.getSource().getId().equals(accountId);
            Long counterpart = isDebit ? t.getTarget().getId() : t.getSource().getId();

            if (isDebit) {
                runningBalance = runningBalance.subtract(t.getAmount());
            } else {
                runningBalance = runningBalance.add(t.getAmount());
            }

            entries.add(new StatementEntry(
                    t.getId(),
                    t.getCreatedAt(),
                    isDebit ? DIRECTION_DEBIT : DIRECTION_CREDIT,
                    t.getAmount(),
                    t.getCurrency(),
                    counterpart,
                    t.getDescription(),
                    runningBalance));
        }
        return entries;
    }
}
