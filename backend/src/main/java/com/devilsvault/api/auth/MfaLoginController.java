package com.devilsvault.api.auth;

import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/auth")
public class MfaLoginController {

    private static final String RESOURCE_TYPE_USER = "user";

    private final UserRepository users;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditEventService audit;

    public MfaLoginController(
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

    public record MfaLoginRequest(@NotBlank String partialToken, @NotBlank String code) {
    }

    @PostMapping("/login/mfa")
    @Transactional
    public ResponseEntity<AuthController.LoginResponse> loginMfa(
            @Valid @RequestBody MfaLoginRequest req, HttpServletRequest httpReq) {
        Claims claims;
        try {
            claims = jwtService.parse(req.partialToken());
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired partial token", ex);
        }

        Boolean mfaPending = claims.get("mfa_pending", Boolean.class);
        if (!Boolean.TRUE.equals(mfaPending)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not a partial MFA token");
        }

        String username = claims.getSubject();
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.isMfaEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA not enabled for this user");
        }

        boolean validTotp = mfaService.verifyCode(user.getMfaSecret(), req.code());
        boolean validRecovery = !validTotp
                && mfaService.verifyRecoveryCode(user.getMfaRecoveryCodesHash(), req.code());

        if (!validTotp && !validRecovery) {
            audit.record(AuditEventType.MFA_LOGIN_FAILURE, AuditOutcome.FAILURE,
                    username, RESOURCE_TYPE_USER, String.valueOf(user.getId()),
                    Map.of("reason", "invalid_code"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid MFA code");
        }

        if (validRecovery) {
            String updatedHashes = mfaService.consumeRecoveryCode(
                    user.getMfaRecoveryCodesHash(), req.code());
            user.setMfaRecoveryCodesHash(updatedHashes);
            users.save(user);
        }

        String deviceLabel = httpReq.getHeader("User-Agent");
        RefreshTokenService.TokenPair pair = refreshTokenService.issueTokenPair(user, deviceLabel);
        audit.recordOnCommit(AuditEventType.MFA_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                username, RESOURCE_TYPE_USER, String.valueOf(user.getId()), Map.of());

        return ResponseEntity.ok(new AuthController.LoginResponse(
                pair.accessToken(), pair.refreshToken(), pair.expiresIn(), null, null));
    }
}
