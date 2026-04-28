package com.devilsvault.api.auth;

import com.devilsvault.api.user.Role;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginRateLimiter rateLimiter;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginRateLimiter rateLimiter) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.rateLimiter = rateLimiter;
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

    public record AuthResponse(String token, String username, String role) {
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (users.existsByEmail(req.email())) {
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
            // Covers the TOCTOU window between existsBy* checks above and save():
            // a concurrent registration with the same username or email can slip through and
            // get rejected by the DB UNIQUE constraint. Surface it as 409, not 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already registered", ex);
        }
        return new AuthResponse(jwt.issue(u.getUsername(), u.getRole().name()), u.getUsername(), u.getRole().name());
    }

    // Fixed BCrypt hash used to equalise the work done on the unknown-username and
    // disabled-account paths so login response timing cannot be used to distinguish
    // them from a live account with a wrong password.
    private static final String DUMMY_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO6Vb2r0F7fLYgC9Y7Y4g3WQw3H0pT7UC";

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        rateLimiter.onSuccessfulLogin(req.username());
        AuthResponse body = new AuthResponse(jwt.issue(u.getUsername(), u.getRole().name()), u.getUsername(), u.getRole().name());
        return ResponseEntity.ok(body);
    }

    /**
     * Resolves the client IP using {@link HttpServletRequest#getRemoteAddr()}.
     * When the backend is deployed behind a trusted reverse proxy, configure
     * {@code server.forward-headers-strategy=framework} so Spring populates
     * remote-addr from {@code X-Forwarded-For}; we deliberately do not parse
     * that header here to avoid trusting a header any client can set.
     */
    private static String clientIp(HttpServletRequest req) {
        String addr = req.getRemoteAddr();
        return addr == null ? "unknown" : addr;
    }
}
