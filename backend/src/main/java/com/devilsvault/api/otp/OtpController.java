package com.devilsvault.api.otp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/otp")
@Tag(name = "OTP", description = "One-time-password issuance and verification")
public class OtpController {

    private final OtpService service;

    public OtpController(OtpService service) {
        this.service = service;
    }

    public record OtpSendRequest(@NotNull Otp.Purpose purpose) { }

    public record OtpSendResponse(Long otpId, String purpose) {
        static OtpSendResponse from(Otp o) {
            return new OtpSendResponse(o.getId(), o.getPurpose().name());
        }
    }

    public record OtpVerifyRequest(
            @NotNull Otp.Purpose purpose,
            @NotBlank @Pattern(regexp = "\\d{6}") String code) { }

    public record OtpVerifyResponse(boolean verified, String purpose) {
        static OtpVerifyResponse from(Otp o) {
            return new OtpVerifyResponse(o.isVerified(), o.getPurpose().name());
        }
    }

    @PostMapping("/send")
    @Operation(summary = "Issue a new OTP for the authenticated user")
    public OtpSendResponse send(@Valid @RequestBody OtpSendRequest req, Principal principal) {
        return OtpSendResponse.from(service.send(principal.getName(), req.purpose()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an OTP for the authenticated user")
    public OtpVerifyResponse verify(@Valid @RequestBody OtpVerifyRequest req, Principal principal) {
        return OtpVerifyResponse.from(service.verify(principal.getName(), req.purpose(), req.code()));
    }
}
