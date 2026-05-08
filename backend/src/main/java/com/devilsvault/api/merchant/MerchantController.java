package com.devilsvault.api.merchant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
@Tag(name = "Merchants", description = "Customer-merchant authorisations and payments")
public class MerchantController {

    private final MerchantService service;

    public MerchantController(MerchantService service) {
        this.service = service;
    }

    public record AuthorizeRequest(@NotNull Long merchantId) { }

    public record PaymentRequest(
            @NotNull Long authorizationId,
            @NotNull Long customerAccountId,
            @NotNull Long merchantAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 255) String description) { }

    @PostMapping("/authorize")
    @Operation(summary = "Customer creates an authorisation for a merchant")
    public MerchantAuthorizationDto authorize(@Valid @RequestBody AuthorizeRequest body, Principal principal) {
        return MerchantAuthorizationDto.from(service.authorize(principal.getName(), body.merchantId()));
    }

    @PutMapping("/authorizations/{id}/accept")
    @Operation(summary = "Merchant accepts a pending customer authorisation")
    public MerchantAuthorizationDto accept(@PathVariable Long id, Principal principal) {
        return MerchantAuthorizationDto.from(service.acceptAuthorization(id, principal.getName()));
    }

    @GetMapping("/authorizations")
    @Operation(summary = "List authorisations for the authenticated user (merchant or customer)")
    public List<MerchantAuthorizationDto> listAuthorizations(Principal principal) {
        // Merchants see everything inbound; customers see everything they raised. The service
        // delegates to whichever side matches the authenticated user.
        List<MerchantAuthorization> mine = service.listForMerchant(principal.getName());
        if (mine.isEmpty()) {
            mine = service.listForCustomer(principal.getName());
        }
        return mine.stream().map(MerchantAuthorizationDto::from).toList();
    }

    @PostMapping("/payments")
    @Operation(summary = "Merchant initiates a payment against an active authorisation")
    public MerchantPaymentDto pay(@Valid @RequestBody PaymentRequest body, Principal principal) {
        return MerchantPaymentDto.from(service.pay(
                principal.getName(),
                body.authorizationId(),
                body.customerAccountId(),
                body.merchantAccountId(),
                body.amount(),
                body.description()));
    }
}
