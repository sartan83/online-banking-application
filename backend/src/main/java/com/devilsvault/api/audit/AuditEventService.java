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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records hash-chained audit events. Each new entry's hash is computed as
 * {@code HMAC-SHA-256(secret, prev_hash || "\n" || canonical_payload)},
 * where the canonical payload is a deterministic concatenation of the
 * event's fields. Verification recomputes the chain and compares.
 *
 * <p>Records are written in a programmatic {@code REQUIRES_NEW} transaction
 * so a caller transaction's rollback does not lose the audit trail (e.g. a
 * failed transfer should still leave a {@code TRANSFER_REJECTED} entry).
 *
 * <p>The transactional write happens <em>inside</em> a Java
 * {@code synchronized} block. This ordering is load-bearing: with
 * {@code @Transactional} on the method, Spring's proxy commits the
 * transaction <em>after</em> the method body returns and therefore
 * <em>after</em> the lock is released. Under READ_COMMITTED, a second
 * thread can then enter the lock, read the same uncommitted predecessor as
 * {@code findTopByOrderByIdDesc()}, and produce a forked chain. Pulling the
 * commit inside the lock via {@link TransactionTemplate} closes that race.
 * This is sufficient for single-instance deployments; multi-instance
 * deployments must move to a database-level lock
 * ({@code pg_advisory_xact_lock}) — tracked in {@code docs/dora/backlog.md}.
 */
@Service
public class AuditEventService {

    private static final String HMAC_ALG = "HmacSHA256";

    private final AuditEventRepository repo;
    private final ObjectMapper canonicalMapper;
    private final byte[] secretKey;
    private final TransactionTemplate writeTx;
    private final Object writeLock = new Object();

    public AuditEventService(
            AuditEventRepository repo,
            PlatformTransactionManager txManager,
            @Value("${app.security.audit.secret:${app.security.jwt.secret}}") String secret) {
        this.repo = repo;
        this.canonicalMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.writeTx = new TransactionTemplate(txManager);
        this.writeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.writeTx.setName("audit-event-write");
    }

    public AuditEvent record(
            AuditEventType type,
            AuditOutcome outcome,
            String actorUsername,
            String resourceType,
            String resourceId,
            Map<String, Object> payload) {
        // The synchronized block must wrap the entire transaction lifecycle —
        // BEGIN, read prev_hash, INSERT, COMMIT — otherwise a second writer
        // can read the same prev_hash before the first writer's INSERT is
        // visible under READ_COMMITTED and produce a forked chain. We use a
        // programmatic TransactionTemplate so the commit happens inside the
        // lock; a method-level @Transactional would let the proxy commit
        // after the lock is released.
        synchronized (writeLock) {
            return writeTx.execute(status -> {
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
            });
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
