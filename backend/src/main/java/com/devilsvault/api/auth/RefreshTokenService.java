package com.devilsvault.api.auth;

import com.devilsvault.api.audit.AuditEventService;
import com.devilsvault.api.audit.AuditEventType;
import com.devilsvault.api.audit.AuditOutcome;
import com.devilsvault.api.user.User;
import com.devilsvault.api.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int REFRESH_TOKEN_TTL_DAYS = 30;
    private static final String RESOURCE_TYPE = "refresh_token";

    private final RefreshTokenRepository repo;
    private final UserRepository userRepo;
    private final JwtService jwtService;
    private final AuditEventService audit;
    private final JtiRevocationCache jtiRevocationCache;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repo,
            UserRepository userRepo,
            JwtService jwtService,
            AuditEventService audit,
            JtiRevocationCache jtiRevocationCache) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
        this.audit = audit;
        this.jtiRevocationCache = jtiRevocationCache;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) { }

    @Transactional
    public TokenPair issueTokenPair(User user, String deviceLabel) {
        String rawRefreshToken = generateRawToken();
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setExpiresAt(OffsetDateTime.now().plusDays(REFRESH_TOKEN_TTL_DAYS));
        entity.setDeviceLabel(deviceLabel);
        repo.save(entity);

        String accessToken = jwtService.issue(user.getUsername(), user.getRole().name());
        return new TokenPair(accessToken, rawRefreshToken, jwtService.getExpirationMinutes() * 60L);
    }

    @Transactional
    public TokenPair rotate(String rawRefreshToken, String deviceLabel) {
        String hash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> found = repo.findByTokenHash(hash);

        if (found.isEmpty()) {
            return null;
        }

        RefreshToken stored = found.get();
        String username = stored.getUser().getUsername();

        if (stored.isRevoked()) {
            revokeAllForUser(stored.getUser());
            audit.record(AuditEventType.AUTH_REFRESH_REUSE_DETECTED, AuditOutcome.CRITICAL,
                    username, RESOURCE_TYPE, String.valueOf(stored.getId()),
                    Map.of("reason", "refresh_token_reuse"));
            return null;
        }

        if (stored.isExpired()) {
            audit.record(AuditEventType.AUTH_REFRESH_FAILURE, AuditOutcome.FAILURE,
                    username, RESOURCE_TYPE, String.valueOf(stored.getId()),
                    Map.of("reason", "expired"));
            return null;
        }

        stored.setRevokedAt(OffsetDateTime.now());
        TokenPair newPair = issueTokenPair(stored.getUser(), deviceLabel);

        String newHash = sha256Hex(newPair.refreshToken());
        Optional<RefreshToken> newEntity = repo.findByTokenHash(newHash);
        newEntity.ifPresent(rt -> stored.setReplacedById(rt.getId()));
        repo.save(stored);

        audit.record(AuditEventType.AUTH_REFRESH_SUCCESS, AuditOutcome.SUCCESS,
                username, RESOURCE_TYPE, String.valueOf(stored.getId()), Map.of());
        return newPair;
    }

    @Transactional
    public boolean revokeToken(String rawRefreshToken, String username) {
        String hash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> found = repo.findByTokenHash(hash);
        if (found.isEmpty()) {
            return false;
        }
        RefreshToken stored = found.get();
        if (!stored.getUser().getUsername().equals(username)) {
            return false;
        }
        if (stored.isRevoked()) {
            return false;
        }
        stored.setRevokedAt(OffsetDateTime.now());
        repo.save(stored);
        return true;
    }

    @Transactional
    public int revokeAllForUser(User user) {
        List<RefreshToken> active = repo.findByUserIdAndRevokedAtIsNull(user.getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken t : active) {
            t.setRevokedAt(now);
            repo.save(t);
        }
        return active.size();
    }

    public JtiRevocationCache getJtiRevocationCache() {
        return jtiRevocationCache;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
