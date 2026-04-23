package com.devilsvault.api.account;

import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository repository;

    public AccountController(AccountRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AccountDto> myAccounts(Principal principal) {
        return repository.findByOwnerUsername(principal.getName()).stream()
                .map(AccountDto::from)
                .toList();
    }
}
