package com.devilsvault.api.auth;

import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private static final String RESOURCE_TYPE_USER = "user";

    private final UserRepository users;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventService audit;

    public MfaController(
            UserRepository users,
            MfaService mfaService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuditEventService audit) {
        this.users = users;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.audit = audit;
    }

    public record EnrollResponse(String secret, String otpauthUrl, List<String> recoveryCodes) {
    }

    public record MfaCodeRequest(@NotBlank String code) {
    }

    public record MfaLoginRequest(@NotBlank String partialToken, @NotBlank String code) {
    }

    public record MfaLoginResponse(String accessToken, String refreshToken, long expiresIn) {
    }

    @PostMapping("/enroll")
    @Transactional
    public EnrollResponse enroll(Principal principal) {
        User user = resolveUser(principal);
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA already enabled");
        }
        String secret = mfaService.generateSecret();
        List<String> recoveryCodes = mfaService.generateRecoveryCodes();
        String hashedCodes = mfaService.hashRecoveryCodes(recoveryCodes);
        String base32Secret = mfaService.toBase32(secret);
        String otpauthUrl = mfaService.buildOtpAuthUrl(user.getUsername(), base32Secret);

        user.setMfaSecret(secret);
        user.setMfaRecoveryCodesHash(hashedCodes);
        user.setMfaEnrolledAt(OffsetDateTime.now());
        users.save(user);

        return new EnrollResponse(base32Secret, otpauthUrl, recoveryCodes);
    }

    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<Map<String, Object>> verify(
            Principal principal, @Valid @RequestBody MfaCodeRequest req) {
        User user = resolveUser(principal);
        if (user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA already enabled");
        }
        if (user.getMfaSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Must enroll first");
        }
        if (!mfaService.verifyCode(user.getMfaSecret(), req.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TOTP code");
        }
        user.setMfaEnabled(true);
        users.save(user);
        audit.recordOnCommit(AuditEventType.MFA_ENABLED, AuditOutcome.SUCCESS,
                user.getUsername(), RESOURCE_TYPE_USER, String.valueOf(user.getId()), Map.of());
        return ResponseEntity.ok(Map.of("mfaEnabled", true));
    }

    @PostMapping("/disable")
    @Transactional
    public ResponseEntity<Map<String, Object>> disable(
            Principal principal, @Valid @RequestBody MfaCodeRequest req) {
        User user = resolveUser(principal);
        if (!user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA not enabled");
        }
        boolean validTotp = mfaService.verifyCode(user.getMfaSecret(), req.code());
        boolean validRecovery = !validTotp
                && mfaService.verifyRecoveryCode(user.getMfaRecoveryCodesHash(), req.code());
        if (!validTotp && !validRecovery) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid code");
        }
        user.setMfaSecret(null);
        user.setMfaEnabled(false);
        user.setMfaEnrolledAt(null);
        user.setMfaRecoveryCodesHash(null);
        users.save(user);
        audit.recordOnCommit(AuditEventType.MFA_DISABLED, AuditOutcome.SUCCESS,
                user.getUsername(), RESOURCE_TYPE_USER, String.valueOf(user.getId()), Map.of());
        return ResponseEntity.ok(Map.of("mfaEnabled", false));
    }

    private User resolveUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
