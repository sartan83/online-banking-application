package com.devilsvault.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class AuditEventServiceTest {

    @Autowired
    AuditEventService service;

    @Autowired
    AuditEventRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @Transactional
    void clear() {
        repo.deleteAllInBatch();
    }

    @AfterEach
    @Transactional
    void cleanup() {
        repo.deleteAllInBatch();
    }

    @Test
    void recordsEventsWithLinkedHashChain() {
        AuditEvent first = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of("ip", "127.0.0.1"));
        AuditEvent second = service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "10", Map.of("amount", "5.00"));
        AuditEvent third = service.record(AuditEventType.AUTH_LOGIN_FAILURE, AuditOutcome.FAILURE,
                "mallory", "user", null, Map.of("reason", "bad_password"));

        assertThat(first.getPrevHash()).isNull();
        assertThat(first.getEntryHash()).isNotBlank().hasSize(64);
        assertThat(second.getPrevHash()).isEqualTo(first.getEntryHash());
        assertThat(third.getPrevHash()).isEqualTo(second.getEntryHash());

        assertThat(service.verifyChain().valid()).isTrue();
    }

    @Test
    void verificationDetectsTamperedPayload() {
        service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of());
        AuditEvent toTamper = service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "10", Map.of("amount", "5.00"));
        service.record(AuditEventType.TRANSFER_COMPLETED, AuditOutcome.SUCCESS,
                "alice", "transfer", "11", Map.of("amount", "1.00"));

        // Tamper at the SQL layer — JPA marks audit columns updatable=false on
        // purpose, so a hostile actor with DB access is the realistic threat
        // model. Verification must catch it.
        jdbc.update("UPDATE audit_event SET payload = ? WHERE id = ?",
                "{\"amount\":\"5000.00\"}", toTamper.getId());

        AuditEventService.ChainStatus status = service.verifyChain();
        assertThat(status.valid()).isFalse();
        assertThat(status.firstInvalidId()).isEqualTo(toTamper.getId());
        assertThat(status.reason()).contains("entry_hash");
    }

    @Test
    void verificationDetectsBrokenLink() {
        AuditEvent first = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", Map.of());
        AuditEvent second = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "bob", "user", "2", Map.of());

        jdbc.update("UPDATE audit_event SET prev_hash = ? WHERE id = ?",
                "0".repeat(64), second.getId());

        AuditEventService.ChainStatus status = service.verifyChain();
        assertThat(status.valid()).isFalse();
        assertThat(status.firstInvalidId()).isEqualTo(second.getId());
        assertThat(status.reason()).contains("prev_hash");

        // The original first event remains untouched.
        List<AuditEvent> all = repo.findAllByOrderByIdAsc();
        assertThat(all.get(0).getId()).isEqualTo(first.getId());
    }

    @Test
    void concurrentRecordsProduceUnforkedChain() throws Exception {
        // Regression: with @Transactional on record(), Spring's proxy commits
        // *after* the synchronized block exits. Under READ_COMMITTED a second
        // writer can read the same prev_hash and fork the chain. The fix
        // wraps the transaction *inside* the lock, so simultaneous writers
        // serialise. This test hammers record() from many threads and
        // verifies (a) every entry committed, (b) verifyChain() is happy,
        // and (c) no two entries share a prev_hash (the fingerprint of a
        // fork).
        int writers = 8;
        int perWriter = 25;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            IntStream.range(0, writers).forEach(w ->
                    pool.submit(() -> {
                        start.await();
                        for (int i = 0; i < perWriter; i++) {
                            service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                                    "user-" + w, "user", String.valueOf(i),
                                    Map.of("worker", w, "i", i));
                        }
                        return null;
                    }));
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        List<AuditEvent> all = repo.findAllByOrderByIdAsc();
        assertThat(all).hasSize(writers * perWriter);

        AuditEventService.ChainStatus status = service.verifyChain();
        assertThat(status.valid())
                .as("chain integrity after %d concurrent writes", all.size())
                .isTrue();

        // A forked chain manifests as two entries pointing at the same
        // prev_hash. Equivalently, every non-null prev_hash must be unique.
        long distinctPrev = all.stream()
                .map(AuditEvent::getPrevHash)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        assertThat(distinctPrev)
                .as("each non-genesis entry must have a unique predecessor hash")
                .isEqualTo(all.size() - 1L);
    }

    @Test
    void payloadIsCanonicalisedDeterministically() {
        AuditEvent a = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", new java.util.LinkedHashMap<>(Map.of("b", 2, "a", 1)));
        AuditEvent b = service.record(AuditEventType.AUTH_LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "alice", "user", "1", new java.util.LinkedHashMap<>(Map.of("a", 1, "b", 2)));
        // Different map insertion order, same canonical JSON => same payload column.
        assertThat(a.getPayload()).isEqualTo(b.getPayload());
    }
}
