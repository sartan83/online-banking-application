package com.devilsvault.api.credit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-cards")
@Tag(name = "Credit cards", description = "Credit card accounts and payments")
public class CreditCardController {

    private final CreditCardService service;

    public CreditCardController(CreditCardService service) {
        this.service = service;
    }

    public record OpenCardResponse(Long id, String maskedCardNumber) {
        static OpenCardResponse from(CreditAccount c) {
            return new OpenCardResponse(c.getId(), CreditCardDto.from(c).maskedCardNumber());
        }
    }

    public record PaymentRequest(
            @NotNull Long creditAccountId,
            @NotNull Long sourceAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount) { }

    @GetMapping
    @Operation(summary = "List the authenticated user's credit cards")
    public List<CreditCardDto> list(Principal principal) {
        return service.listForOwner(principal.getName()).stream().map(CreditCardDto::from).toList();
    }

    @PostMapping
    @Operation(summary = "Open a new credit card for the authenticated user")
    public OpenCardResponse open(Principal principal) {
        return OpenCardResponse.from(service.openCard(principal.getName()));
    }

    @PostMapping("/payment")
    @Operation(summary = "Pay a credit card balance from a checking/savings account")
    public CreditTransactionDto pay(@Valid @RequestBody PaymentRequest req, Principal principal) {
        return CreditTransactionDto.from(service.makePayment(
                req.creditAccountId(), req.sourceAccountId(), req.amount(), principal.getName()));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "List transactions for a credit card")
    public List<CreditTransactionDto> transactions(@PathVariable Long id, Principal principal) {
        return service.transactions(id, principal.getName()).stream()
                .map(CreditTransactionDto::from)
                .toList();
    }
}
