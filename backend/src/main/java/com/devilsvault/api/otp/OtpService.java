package com.devilsvault.api.otp;

import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Modern replacement for {@code dao/userauthentication/OtpDaoImpl}.
 *
 * <p>Preserves the legacy semantics:
 * <ul>
 *   <li>3-minute TTL ({@link #TTL_SECONDS})</li>
 *   <li>3-attempt cap before the active code is invalidated</li>
 *   <li>The active row is replaced (not appended) when a new code is requested</li>
 * </ul>
 */
@Service
public class OtpService {

    public static final int MAX_ATTEMPTS = 3;
    public static final long TTL_SECONDS = 180L;

    private final OtpRepository repository;
    private final UserRepository users;
    private final OtpCodeGenerator generator;
    private final OtpDeliveryService delivery;

    public OtpService(OtpRepository repository,
                      UserRepository users,
                      OtpCodeGenerator generator,
                      OtpDeliveryService delivery) {
        this.repository = repository;
        this.users = users;
        this.generator = generator;
        this.delivery = delivery;
    }

    @Transactional
    public Otp send(String username, Otp.Purpose purpose) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        repository.deleteByUserIdAndPurpose(user.getId(), purpose);
        String code = generator.generate();
        Otp otp = new Otp();
        otp.setUser(user);
        otp.setCode(code);
        otp.setPurpose(purpose);
        otp.setExpiresAt(OffsetDateTime.now().plusSeconds(TTL_SECONDS));
        Otp saved = repository.save(otp);
        delivery.deliver(user.getEmail(), code, purpose);
        return saved;
    }

    // Increment-on-failure must commit even when we ultimately throw a 4xx, so callers see the
    // accumulated attempt count on the next call. The same applies to deletion on lockout/expiry.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Otp verify(String username, Otp.Purpose purpose, String submittedCode) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Otp otp = repository
                .findFirstByUserIdAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active OTP"));
        if (otp.getExpiresAt().isBefore(OffsetDateTime.now())) {
            repository.delete(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        otp.setAttempts(otp.getAttempts() + 1);
        if (!otp.getCode().equals(submittedCode)) {
            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                repository.delete(otp);
                repository.flush();
                throw new ResponseStatusException(HttpStatus.LOCKED, "Too many invalid attempts");
            }
            repository.saveAndFlush(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }
        otp.setVerified(true);
        return repository.save(otp);
    }
}
