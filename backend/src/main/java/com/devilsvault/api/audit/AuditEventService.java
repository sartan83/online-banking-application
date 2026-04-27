package com.devilsvault.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records hash-chained audit events. Each new entry's hash is computed as
 * {@code HMAC-SHA-256(secret, prev_hash || "\n" || canonical_payload)},
 * where the canonical payload is a deterministic concatenation of the
 * event's fields. Verification recomputes the chain and compares.
 *
 * <p>Records are written in {@link Propagation#REQUIRES_NEW} so that a
 * caller transaction's rollback does not lose the audit trail (e.g. a
 * failed transfer should still leave a {@code TRANSFER_REJECTED} entry).
 *
 * <p>The {@link #record} method is {@code synchronized} to prevent two
 * concurrent records from reading the same {@code prev_hash} and producing
 * a forked chain. This is sufficient for single-instance deployments;
 * multi-instance deployments must move to a database-level lock or a
 * single-writer architecture (tracked under DORA Phase 3).
 */
@Service
public class AuditEventService {

    private static final String HMAC_ALG = "HmacSHA256";

    private final AuditEventRepository repo;
    private final ObjectMapper canonicalMapper;
    private final byte[] secretKey;
    private final Object writeLock = new Object();

    public AuditEventService(
            AuditEventRepository repo,
            @Value("${app.security.audit.secret:${app.security.jwt.secret}}") String secret) {
        this.repo = repo;
        this.canonicalMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(
            AuditEventType type,
            AuditOutcome outcome,
            String actorUsername,
            String resourceType,
            String resourceId,
            Map<String, Object> payload) {
        // Block-level lock so two concurrent records cannot read the same
        // prev_hash and produce a forked chain. Sufficient for single-instance
        // deployments; multi-instance must move to a DB lock.
        synchronized (writeLock) {
            String prevHash = repo.findTopByOrderByIdDesc()
                    .map(AuditEvent::getEntryHash)
                    .orElse(null);

            String correlationId = currentCorrelationId();
            String actorIp = currentClientIp();
            String canonicalPayload = canonicalize(payload);

            String entryHash = hmacSha256Hex(prevHash, type, outcome, actorUsername,
                    actorIp, correlationId, resourceType, resourceId, canonicalPayload);

            AuditEvent event = new AuditEvent();
            event.setEventType(type);
            event.setOutcome(outcome);
            event.setActorUsername(actorUsername);
            event.setActorIp(actorIp);
            event.setCorrelationId(correlationId);
            event.setResourceType(resourceType);
            event.setResourceId(resourceId);
            event.setPayload(canonicalPayload);
            event.setPrevHash(prevHash);
            event.setEntryHash(entryHash);
            return repo.save(event);
        }
    }

    /**
     * Recomputes the chain in id order. Returns {@link ChainStatus#valid()}
     * if every entry's hash matches its declared prev_hash + canonical
     * fields; otherwise returns the id of the first divergent row.
     */
    @Transactional(readOnly = true)
    public ChainStatus verifyChain() {
        String expectedPrev = null;
        for (AuditEvent e : repo.findAllByOrderByIdAsc()) {
            if (!java.util.Objects.equals(expectedPrev, e.getPrevHash())) {
                return ChainStatus.broken(e.getId(), "prev_hash mismatch");
            }
            String recomputed = hmacSha256Hex(
                    expectedPrev,
                    e.getEventType(),
                    e.getOutcome(),
                    e.getActorUsername(),
                    e.getActorIp(),
                    e.getCorrelationId(),
                    e.getResourceType(),
                    e.getResourceId(),
                    e.getPayload());
            if (!recomputed.equals(e.getEntryHash())) {
                return ChainStatus.broken(e.getId(), "entry_hash mismatch");
            }
            expectedPrev = e.getEntryHash();
        }
        return ChainStatus.ok();
    }

    private String hmacSha256Hex(
            String prevHash,
            AuditEventType type,
            AuditOutcome outcome,
            String actorUsername,
            String actorIp,
            String correlationId,
            String resourceType,
            String resourceId,
            String canonicalPayload) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(prevHash == null ? "" : prevHash).append('\n')
                .append(type.name()).append('|')
                .append(outcome.name()).append('|')
                .append(nullToEmpty(actorUsername)).append('|')
                .append(nullToEmpty(actorIp)).append('|')
                .append(nullToEmpty(correlationId)).append('|')
                .append(nullToEmpty(resourceType)).append('|')
                .append(nullToEmpty(resourceId)).append('|')
                .append(canonicalPayload);
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALG));
            byte[] sig = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", ex);
        }
    }

    private String canonicalize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        try {
            return canonicalMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit payload is not JSON-serialisable", ex);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String currentCorrelationId() {
        String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        return fromMdc == null ? null : fromMdc;
    }

    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            if (req != null) {
                return req.getRemoteAddr();
            }
        }
        return null;
    }

    public record ChainStatus(boolean valid, Long firstInvalidId, String reason) {
        public static ChainStatus ok() { return new ChainStatus(true, null, null); }
        public static ChainStatus broken(Long id, String reason) { return new ChainStatus(false, id, reason); }
    }
}
