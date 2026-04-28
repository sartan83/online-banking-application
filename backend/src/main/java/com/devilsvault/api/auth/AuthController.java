package com.devilsvault.api.auth;

import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String RESOURCE_TYPE_USER = "user";
    private static final String PAYLOAD_KEY_REASON = "reason";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuditEventService audit;

    public AuthController(
            UserRepository users,
            PasswordEncoder encoder,
            JwtService jwt,
            RefreshTokenService refreshTokenService,
            LoginRateLimiter rateLimiter,
            AuditEventService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 128) String fullName) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String refreshToken, long expiresIn,
                                Boolean mfaRequired, String partialToken) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String token, String username, String role) {
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            audit.record(AuditEventType.AUTH_REGISTER_FAILURE, AuditOutcome.FAILURE,
                    req.username(), RESOURCE_TYPE_USER, null, Map.of(PAYLOAD_KEY_REASON, "username_taken"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (users.existsByEmail(req.email())) {
            audit.record(AuditEventType.AUTH_REGISTER_FAILURE, AuditOutcome.FAILURE,
                    req.username(), RESOURCE_TYPE_USER, null, Map.of(PAYLOAD_KEY_REASON, "email_taken"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User u = new User();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName());
        u.setRole(Role.CUSTOMER);
        try {
            users.save(u);
        } catch (DataIntegrityViolationException ex) {
            audit.record(AuditEventType.AUTH_REGISTER_FAILURE, AuditOutcome.FAILURE,
                    req.username(), RESOURCE_TYPE_USER, null, Map.of(PAYLOAD_KEY_REASON, "unique_constraint"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered", ex);
        }
        audit.record(AuditEventType.AUTH_REGISTER_SUCCESS, AuditOutcome.SUCCESS,
                u.getUsername(), RESOURCE_TYPE_USER, String.valueOf(u.getId()), Map.of());
        return new AuthResponse(jwt.issue(u.getUsername(), u.getRole().name()), u.getUsername(), u.getRole().name());
    }

    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO6Vb2r0F7fLYgC9Y7Y4g3WQw3H0pT7UC";

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String ip = clientIp(httpReq);
        LoginRateLimiter.Decision decision = rateLimiter.tryAcquire(req.username(), ip);
        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1L, decision.retryAfter().toSeconds());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                    .build();
        }

        User u = users.findByUsername(req.username()).orElse(null);
        String hash = u != null ? u.getPasswordHash() : DUMMY_HASH;
        boolean passwordOk = encoder.matches(req.password(), hash);
        if (u == null || !u.isEnabled() || !passwordOk) {
            String reason = u == null ? "unknown_user" : (!u.isEnabled() ? "disabled" : "bad_password");
            audit.record(AuditEventType.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE,
                    req.username(), RESOURCE_TYPE_USER, u == null ? null : String.valueOf(u.getId()),
                    Map.of(PAYLOAD_KEY_REASON, reason));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        rateLimiter.onSuccessfulLogin(req.username());

        if (u.isMfaEnabled()) {
            String partialToken = jwt.issuePartialMfaToken(u.getUsername(), u.getRole().name());
            LoginResponse body = new LoginResponse(null, null, 0, true, partialToken);
            return ResponseEntity.ok(body);
        }

        audit.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                u.getUsername(), RESOURCE_TYPE_USER, String.valueOf(u.getId()), Map.of());

        String deviceLabel = httpReq.getHeader("User-Agent");
        RefreshTokenService.TokenPair pair = refreshTokenService.issueTokenPair(u, deviceLabel);
        LoginResponse body = new LoginResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn(),
                null, null);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshRequest req, HttpServletRequest httpReq) {
        String deviceLabel = httpReq.getHeader("User-Agent");
        RefreshTokenService.TokenPair pair = refreshTokenService.rotate(req.refreshToken(), deviceLabel);
        if (pair == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        return ResponseEntity.ok(new LoginResponse(pair.accessToken(), pair.refreshToken(), pair.expiresIn(),
                null, null));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String username = principal.getName();
        boolean revoked = refreshTokenService.revokeToken(req.refreshToken(), username);
        if (!revoked) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token not found");
        }
        audit.record(AuditEventType.AUTH_LOGOUT, AuditOutcome.SUCCESS,
                username, RESOURCE_TYPE_USER, null, Map.of());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String username = principal.getName();
        User u = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        refreshTokenService.revokeAllForUser(u);
        audit.record(AuditEventType.AUTH_LOGOUT_ALL, AuditOutcome.SUCCESS,
                username, RESOURCE_TYPE_USER, String.valueOf(u.getId()), Map.of());
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest req) {
        String addr = req.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
