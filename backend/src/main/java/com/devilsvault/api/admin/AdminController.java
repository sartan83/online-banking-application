package com.devilsvault.api.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public Page<AdminUserDto> listUsers(
            @RequestParam(required = false, defaultValue = "") @Size(max = 128) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return adminService.listUsers(q, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailDto getUser(@PathVariable Long id) {
        return adminService.getUser(id);
    }

    @PostMapping("/accounts/{id}/freeze")
    public AdminAccountDto freezeAccount(@PathVariable Long id, Principal principal) {
        return adminService.freezeAccount(id, principal.getName());
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public AdminAccountDto unfreezeAccount(@PathVariable Long id, Principal principal) {
        return adminService.unfreezeAccount(id, principal.getName());
    }

    @GetMapping("/audit")
    public Page<AdminAuditDto> listAudit(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String actorUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime until,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return adminService.listAudit(eventType, outcome, actorUsername, since, until, page, size);
    }

    @GetMapping("/audit/integrity")
    public AdminIntegrityDto checkAuditIntegrity(Principal principal) {
        return adminService.checkAuditIntegrity(principal.getName());
    }

    @PostMapping("/sessions/{userId}/revoke")
    public org.springframework.http.ResponseEntity<Void> revokeSessions(
            @PathVariable Long userId, Principal principal) {
        adminService.revokeUserSessions(userId, principal.getName());
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
