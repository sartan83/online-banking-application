package com.devilsvault.api.account;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository repository;
    private final StatementService statementService;

    public AccountController(AccountRepository repository, StatementService statementService) {
        this.repository = repository;
        this.statementService = statementService;
    }

    @GetMapping
    public List<AccountDto> myAccounts(Principal principal) {
        return repository.findByOwnerUsername(principal.getName()).stream()
                .map(AccountDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountDetailDto accountDetail(@PathVariable Long id, Principal principal) {
        return statementService.getAccountDetail(principal.getName(), id);
    }

    @GetMapping("/{id}/statement")
    public StatementPage statement(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        int effectiveSize = Math.min(Math.max(size, 1), 100);
        return statementService.getStatement(
                principal.getName(), id, since, until, direction,
                PageRequest.of(page, effectiveSize));
    }
}
