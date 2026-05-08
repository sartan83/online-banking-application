package com.devilsvault.api.admin;

import com.devilsvault.api.transfer.Transfer;
import com.devilsvault.api.transfer.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Internal user management, audit logs and approvals")
public class AdminController {

    private static final int MAX_LOGS = 500;

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @Operation(summary = "List all users")
    public List<UserSummary> users(Principal principal) {
        return service.listUsers(principal.getName()).stream().map(UserSummary::from).toList();
    }

    @PutMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role")
    public UserSummary changeRole(@PathVariable Long id,
                                  @Valid @RequestBody ChangeRoleRequest body,
                                  Principal principal) {
        return UserSummary.from(service.changeRole(id, body.role(), principal.getName()));
    }

    @PutMapping("/users/{id}/enabled")
    @Operation(summary = "Enable or disable a user account")
    public UserSummary setEnabled(@PathVariable Long id,
                                  @RequestParam boolean enabled,
                                  Principal principal) {
        return UserSummary.from(service.setEnabled(id, enabled, principal.getName()));
    }

    @GetMapping("/logs")
    @Operation(summary = "List recent audit log entries")
    public List<AuditLogDto> logs(@RequestParam(defaultValue = "100") int limit, Principal principal) {
        int capped = Math.min(Math.max(limit, 1), MAX_LOGS);
        return service.recentAuditLogs(principal.getName(), capped).stream().map(AuditLogDto::from).toList();
    }

    @GetMapping("/transactions/pending")
    @Operation(summary = "List pending transfers awaiting approval")
    public List<TransferResponse> pendingTransactions(Principal principal) {
        return service.listPendingTransfers(principal.getName()).stream().map(TransferResponse::from).toList();
    }

    @PutMapping("/transactions/{id}/approve")
    @Operation(summary = "Approve a pending transfer")
    public TransferResponse approveTransaction(@PathVariable Long id, Principal principal) {
        Transfer t = service.approveTransfer(id, principal.getName());
        return TransferResponse.from(t);
    }

    @GetMapping("/registrations")
    @Operation(summary = "List pending registrations")
    public List<PendingRegistrationDto> pendingRegistrations(Principal principal) {
        return service.listPendingRegistrations(principal.getName()).stream()
                .map(PendingRegistrationDto::from)
                .toList();
    }

    @PostMapping("/registrations")
    @Operation(summary = "Pre-register an account that requires approval")
    public PendingRegistrationDto createRegistration(
            @Valid @RequestBody CreatePendingRegistrationRequest body,
            Principal principal) {
        return PendingRegistrationDto.from(
                service.createPendingRegistration(principal.getName(), body));
    }

    @PutMapping("/registrations/{id}/approve")
    @Operation(summary = "Approve a pending registration and create the user")
    public PendingRegistrationDto approveRegistration(@PathVariable Long id, Principal principal) {
        return PendingRegistrationDto.from(
                service.approveRegistration(id, principal.getName()));
    }

    @PutMapping("/registrations/{id}/reject")
    @Operation(summary = "Reject a pending registration")
    public PendingRegistrationDto rejectRegistration(@PathVariable Long id, Principal principal) {
        return PendingRegistrationDto.from(
                service.rejectRegistration(id, principal.getName()));
    }
}
