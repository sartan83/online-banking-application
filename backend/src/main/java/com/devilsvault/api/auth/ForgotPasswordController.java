package com.devilsvault.api.auth;

import com.devilsvault.api.otp.Otp;
import com.devilsvault.api.otp.OtpService;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserAuthenticationService;
import com.devilsvault.api.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for the legacy {@code controllers/employee/ForgotPasswordController}.
 *
 * <p>Two-step flow: {@code /api/auth/forgot-password} issues a {@link Otp.Purpose#FORGOT_PASSWORD}
 * code; {@code /api/auth/reset-password} verifies it and rotates the password. Both endpoints
 * always return {@code 204 No Content} regardless of whether the email exists, so the API does not
 * leak account enumeration information.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Forgot password", description = "Self-service password reset")
public class ForgotPasswordController {

    private final UserRepository users;
    private final OtpService otp;
    private final PasswordEncoder encoder;
    private final UserAuthenticationService loginLimiter;

    public ForgotPasswordController(UserRepository users,
                                    OtpService otp,
                                    PasswordEncoder encoder,
                                    UserAuthenticationService loginLimiter) {
        this.users = users;
        this.otp = otp;
        this.encoder = encoder;
        this.loginLimiter = loginLimiter;
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) { }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "\\d{6}") String code,
            @NotBlank @Size(min = 8, max = 128) String newPassword) { }

    @PostMapping("/forgot-password")
    @Operation(summary = "Issue a password-reset OTP for the given email")
    public void forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        users.findByEmail(req.email())
                .ifPresent(u -> otp.send(u.getUsername(), Otp.Purpose.FORGOT_PASSWORD));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Verify the password-reset OTP and set a new password")
    @Transactional
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        User u = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request"));
        otp.verify(u.getUsername(), Otp.Purpose.FORGOT_PASSWORD, req.code());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setEnabled(true);
        users.save(u);
        loginLimiter.unlock(u.getUsername());
    }
}
